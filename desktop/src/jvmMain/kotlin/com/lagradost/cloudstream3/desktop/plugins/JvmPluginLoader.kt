package com.lagradost.cloudstream3.desktop.plugins

import com.lagradost.api.Log
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import java.util.zip.ZipInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class JvmPluginLoader(
    private val parentClassLoader: ClassLoader = JvmPluginLoader::class.java.classLoader
) : PluginLoader {

    override val pluginsDirectory: String
        get() = File(System.getProperty("user.home") ?: ".", ".cloudstream/plugins").apply { mkdirs() }.absolutePath

    companion object {
        private const val TAG = "JvmPluginLoader"
    }

    private val loadedPlugins = mutableMapOf<String, BasePlugin>()

    override fun getManifest(filePath: String): BasePlugin.Manifest? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "Plugin file not found: $filePath")
            return null
        }

        // Try reading manifest using JarFile
        try {
            JarFile(file).use { jar ->
                val entry = jar.getJarEntry("manifest.json") ?: jar.getEntry("manifest.json")
                if (entry != null) {
                    jar.getInputStream(entry).bufferedReader().use { reader ->
                        return parseJson<BasePlugin.Manifest>(reader.readText())
                    }
                }
            }
        } catch (e: Throwable) {
            // Fallback to ZipInputStream
            try {
                ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "manifest.json") {
                            return parseJson<BasePlugin.Manifest>(zis.bufferedReader().readText())
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read manifest from $filePath: ${t.message}")
            }
        }
        return null
    }

    override fun loadPlugin(filePath: String): BasePlugin? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                Log.e(TAG, "Plugin file does not exist: $filePath")
                return null
            }

            // Clean up any previous in-memory registration for this plugin before loading
            unloadPluginInMemoryOnly(file.absolutePath)
            unloadPluginInMemoryOnly(file.nameWithoutExtension)

            // 1. Read and parse manifest.json from the jar/zip
            val manifest = getManifest(filePath)
            if (manifest == null) {
                Log.e(TAG, "Failed to load plugin ${file.name}: No manifest.json found")
                return null
            }

            val className = manifest.pluginClassName
            if (className.isNullOrBlank()) {
                Log.e(TAG, "Failed to load plugin ${file.name}: pluginClassName is missing in manifest")
                return null
            }

            // 2. Check if the archive contains classes.dex without .class files
            var jarFileToLoad = file
            var hasDex = false
            var hasClass = false
            try {
                ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".dex")) {
                            hasDex = true
                        } else if (entry.name.endsWith(".class")) {
                            hasClass = true
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (_: Throwable) {}

            if (hasDex && !hasClass) {
                val cacheDir = File(file.parentFile, "cache").apply { mkdirs() }
                val targetJar = File(cacheDir, "${file.nameWithoutExtension}.jar")
                if (!targetJar.exists() || targetJar.lastModified() < file.lastModified()) {
                    try {
                        com.googlecode.d2j.dex.Dex2jar.from(file).to(targetJar.toPath())
                    } catch (e: Throwable) {
                        Log.e(TAG, "Dex2jar conversion failed for ${file.name}: ${e.message}")
                    }
                }
                if (targetJar.exists()) {
                    jarFileToLoad = targetJar
                }
            }

            // 3. Instantiate BasePlugin using JvmPluginClassLoader with ASM bytecode transformation
            val urlClassLoader = JvmPluginClassLoader(arrayOf(jarFileToLoad.toURI().toURL()), parentClassLoader)
            val pluginClass = Class.forName(className, true, urlClassLoader)

            if (!BasePlugin::class.java.isAssignableFrom(pluginClass)) {
                Log.e(TAG, "Plugin class $className in ${file.name} does not extend BasePlugin")
                return null
            }

            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
            pluginInstance.filename = file.absolutePath
            pluginInstance.manifest = manifest

            try {
                val loadWithContext = pluginClass.methods.firstOrNull { 
                    it.name == "load" && it.parameterCount == 1 
                }
                if (loadWithContext != null) {
                    loadWithContext.invoke(pluginInstance, android.content.Context())
                } else {
                    pluginInstance.load()
                }
            } catch (e: Throwable) {
                try {
                    pluginInstance.load()
                } catch (e2: Throwable) {
                    Log.e(TAG, "Error invoking plugin.load() for ${file.name}: ${e2.message}")
                }
            }

            synchronized(loadedPlugins) {
                loadedPlugins[file.absolutePath] = pluginInstance
                loadedPlugins[file.name] = pluginInstance
                loadedPlugins[file.nameWithoutExtension] = pluginInstance
                manifest.name?.let { loadedPlugins[it] = pluginInstance }
            }

            pluginInstance
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load plugin from $filePath: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun matchesProvider(provider: com.lagradost.cloudstream3.MainAPI, filePathOrName: String, rawName: String): Boolean {
        val src = provider.sourcePlugin ?: ""
        val srcFile = File(src)
        val srcName = srcFile.name
        val srcRaw = srcFile.nameWithoutExtension
        val targetName = File(filePathOrName).name
        val provName = provider.name
        val normProvName = provName.replace(" ", "")
        val normTargetRaw = rawName.replace(" ", "").removeSuffix("Provider")

        if (src.isNotBlank()) {
            if (src.equals(filePathOrName, ignoreCase = true) ||
                src.equals(rawName, ignoreCase = true) ||
                srcName.equals(targetName, ignoreCase = true) ||
                srcName.equals(filePathOrName, ignoreCase = true) ||
                srcRaw.equals(rawName, ignoreCase = true) ||
                src.contains(rawName, ignoreCase = true) ||
                filePathOrName.contains(srcRaw, ignoreCase = true)
            ) {
                return true
            }
        }

        if (provName.equals(filePathOrName, ignoreCase = true) ||
            provName.equals(rawName, ignoreCase = true) ||
            provName.equals(targetName, ignoreCase = true) ||
            normProvName.equals(normTargetRaw, ignoreCase = true) ||
            rawName.contains(normProvName, ignoreCase = true) ||
            normTargetRaw.contains(normProvName, ignoreCase = true)
        ) {
            return true
        }

        if (provider.mainUrl.isNotBlank()) {
            val mainUrl = provider.mainUrl
            if (mainUrl.contains(rawName, ignoreCase = true) ||
                (normTargetRaw.length >= 4 && mainUrl.contains(normTargetRaw, ignoreCase = true))
            ) {
                return true
            }
        }

        return false
    }

    private fun unloadPluginInMemoryOnly(filePathOrName: String) {
        val rawName = File(filePathOrName).nameWithoutExtension
        val plugin = synchronized(loadedPlugins) {
            loadedPlugins.remove(filePathOrName)
                ?: loadedPlugins.remove(rawName)
                ?: loadedPlugins.remove(File(filePathOrName).name)
        }

        try {
            plugin?.beforeUnload()
        } catch (_: Throwable) {}

        // Remove from APIHolder.apis
        val apisToRemove = com.lagradost.cloudstream3.APIHolder.apis.withLock {
            com.lagradost.cloudstream3.APIHolder.apis.filter { matchesProvider(it, filePathOrName, rawName) }
        }
        apisToRemove.forEach { provider ->
            com.lagradost.cloudstream3.APIHolder.removePluginMapping(provider)
        }

        // Remove from APIHolder.allProviders
        com.lagradost.cloudstream3.APIHolder.allProviders.withLock {
            com.lagradost.cloudstream3.APIHolder.allProviders.removeAll { provider ->
                matchesProvider(provider, filePathOrName, rawName)
            }
        }

        // Remove from extractorApis
        com.lagradost.cloudstream3.utils.extractorApis.withLock {
            com.lagradost.cloudstream3.utils.extractorApis.removeAll { extractor ->
                val src = extractor.sourcePlugin ?: ""
                val srcRaw = File(src).nameWithoutExtension
                (src.isNotBlank() && (src.contains(rawName, ignoreCase = true) || srcRaw.equals(rawName, ignoreCase = true))) ||
                extractor.name.equals(filePathOrName, ignoreCase = true) ||
                extractor.name.equals(rawName, ignoreCase = true)
            }
        }

        com.lagradost.cloudstream3.APIHolder.notifyProvidersChanged()
    }

    override fun unloadPlugin(filePathOrName: String): Boolean {
        return try {
            unloadPluginInMemoryOnly(filePathOrName)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error unloading plugin $filePathOrName: ${t.message}")
            false
        }
    }
}

class JvmPluginClassLoader(
    urls: Array<java.net.URL>,
    private val parentClassLoader: ClassLoader
) : URLClassLoader(urls, parentClassLoader) {

    override fun findClass(name: String): Class<*> {
        val classPath = name.replace('.', '/') + ".class"
        val resource = findResource(classPath)
        if (resource != null) {
            try {
                val rawBytes = resource.openStream().use { it.readBytes() }
                val transformedBytes = fixBytecode(rawBytes, parentClassLoader)
                return defineClass(name, transformedBytes, 0, transformedBytes.size)
            } catch (_: Throwable) {
                // Ignore and let super.findClass handle it
            }
        }
        return super.findClass(name)
    }

    companion object {
        fun fixBytecode(bytes: ByteArray, parentClassLoader: ClassLoader): ByteArray {
            return try {
                val cr = ClassReader(bytes)
                val cw = ClassWriter(cr, 0)
                val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
                    override fun visitMethod(
                        access: Int,
                        name: String?,
                        descriptor: String?,
                        signature: String?,
                        exceptions: Array<out String>?
                    ): MethodVisitor {
                        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                            override fun visitMethodInsn(
                                opcode: Int,
                                owner: String,
                                methodName: String,
                                descriptor: String,
                                isInterface: Boolean
                            ) {
                                var targetName = methodName
                                if (targetName.contains('_')) {
                                    if (targetName.contains("_impl")) {
                                        val candidate = targetName.replace("_impl", "-impl")
                                        if (owner.startsWith("kotlin/") || owner.startsWith("kotlinx/")) {
                                            targetName = candidate
                                        } else {
                                            try {
                                                val clazz = Class.forName(owner.replace('/', '.'), false, parentClassLoader)
                                                if (clazz.methods.any { it.name == candidate } || clazz.declaredMethods.any { it.name == candidate }) {
                                                    targetName = candidate
                                                }
                                            } catch (_: Throwable) {
                                                targetName = candidate
                                            }
                                        }
                                    } else {
                                        val candidate = targetName.replace('_', '-')
                                        try {
                                            val clazz = Class.forName(owner.replace('/', '.'), false, parentClassLoader)
                                            if (clazz.methods.any { it.name == candidate } || clazz.declaredMethods.any { it.name == candidate }) {
                                                targetName = candidate
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                }
                                super.visitMethodInsn(opcode, owner, targetName, descriptor, isInterface)
                            }
                        }
                    }
                }
                cr.accept(cv, 0)
                cw.toByteArray()
            } catch (_: Throwable) {
                bytes
            }
        }
    }
}
