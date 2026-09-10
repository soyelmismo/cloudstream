package com.lagradost.cloudstream3.shared.plugins

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.PluginLoader
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakePluginLoader(
    override val pluginsDirectory: String? = null
) : PluginLoader {
    val loadedPaths = mutableListOf<String>()
    val unloadedPaths = mutableListOf<String>()

    override fun loadPlugin(filePath: String): BasePlugin? {
        loadedPaths.add(filePath)
        return null
    }

    override fun unloadPlugin(filePathOrName: String): Boolean {
        unloadedPaths.add(filePathOrName)
        return true
    }

    override fun isPluginLoaded(filePathOrName: String): Boolean = true
}

class PluginManagerTest {

    private lateinit var tempDir: File
    private lateinit var fakeLoader: FakePluginLoader
    private lateinit var pluginManager: DefaultPluginManager

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("cs3_test_plugins", "").apply {
            delete()
            mkdirs()
        }
        fakeLoader = FakePluginLoader(pluginsDirectory = tempDir.absolutePath)
        pluginManager = DefaultPluginManager(pluginLoader = fakeLoader)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testPluginsDirectoryResolution() {
        assertEquals(tempDir.absolutePath, pluginManager.pluginsDirectory.absolutePath)
        assertTrue(pluginManager.pluginsDirectory.exists())
    }

    @Test
    fun testInstallLocalExistingPlugin() = runTest {
        val pluginFile = File(tempDir, "LocalPlugin.cs3").apply {
            writeBytes("fake-plugin-content".encodeToByteArray())
        }

        val pluginItem = PluginItem(
            internalName = "LocalPlugin",
            name = "Local Provider",
            localFilePath = pluginFile.absolutePath,
            isEnabled = true
        )

        val result = pluginManager.installPlugin(pluginItem)
        assertTrue(result.isSuccess)
        val installed = result.getOrThrow()
        assertTrue(installed.isInstalled)
        assertTrue(installed.isDownloaded)
        assertEquals(pluginFile.absolutePath, installed.localFilePath)

        assertTrue(fakeLoader.loadedPaths.contains(pluginFile.absolutePath))
    }

    @Test
    fun testInstallDisabledPluginDoesNotLoadIntoEngine() = runTest {
        val pluginFile = File(tempDir, "DisabledPlugin.cs3").apply {
            writeBytes("disabled-content".encodeToByteArray())
        }

        val pluginItem = PluginItem(
            internalName = "DisabledPlugin",
            name = "Disabled Provider",
            localFilePath = pluginFile.absolutePath,
            isEnabled = false
        )

        val result = pluginManager.installPlugin(pluginItem)
        assertTrue(result.isSuccess)
        assertFalse(fakeLoader.loadedPaths.contains(pluginFile.absolutePath))
        assertTrue(fakeLoader.unloadedPaths.contains("DisabledPlugin"))
    }

    @Test
    fun testUninstallPluginDeletesFileAndUnloads() = runTest {
        val pluginFile = File(tempDir, "ToRemove.cs3").apply {
            writeBytes("temp-content".encodeToByteArray())
        }
        assertTrue(pluginFile.exists())

        val result = pluginManager.uninstallPlugin("ToRemove")
        assertTrue(result.isSuccess)
        assertFalse(pluginFile.exists())
        assertTrue(fakeLoader.unloadedPaths.contains(pluginFile.absolutePath))
        assertTrue(fakeLoader.unloadedPaths.contains("ToRemove"))
    }

    @Test
    fun testUninstallPluginWithExtensionDeletesFileAndUnloads() = runTest {
        val pluginFile = File(tempDir, "ToRemoveExt.cs3").apply {
            writeBytes("temp-content".encodeToByteArray())
        }
        assertTrue(pluginFile.exists())

        val result = pluginManager.uninstallPlugin("ToRemoveExt.cs3")
        assertTrue(result.isSuccess)
        assertFalse(pluginFile.exists())
        assertTrue(fakeLoader.unloadedPaths.contains(pluginFile.absolutePath))
        assertTrue(fakeLoader.unloadedPaths.contains("ToRemoveExt"))
    }

    @Test
    fun testDownloadFailurePropagatesEvenIfFileAlreadyExisted() = runTest {
        val pluginFile = File(tempDir, "ExistingPlugin.cs3").apply {
            writeBytes("old-version-content".encodeToByteArray())
        }
        assertTrue(pluginFile.exists())

        val pluginItem = PluginItem(
            internalName = "ExistingPlugin",
            name = "Existing Provider",
            url = "https://invalid-nonexistent-domain-12345.xyz/plugin.cs3",
            localFilePath = pluginFile.absolutePath,
            isEnabled = true
        )

        val result = pluginManager.installPlugin(pluginItem)
        assertTrue(result.isFailure)
        assertEquals("old-version-content", pluginFile.readText())
    }

    @Test
    fun testDirectBaseDirectoryConfiguration() {
        val customDir = File(tempDir, "custom_sub_plugins").apply { mkdirs() }
        val manager = DefaultPluginManager(pluginLoader = null, baseDirectory = customDir)
        assertEquals(customDir.absolutePath, manager.pluginsDirectory.absolutePath)
    }

    @Test
    fun testIsPluginLoaded() {
        assertTrue(pluginManager.isPluginLoaded("Any"))
        val noLoaderManager = DefaultPluginManager(pluginLoader = null)
        assertFalse(noLoaderManager.isPluginLoaded("Any"))
    }

    @Test
    fun testRepositoryManifestParsingAndModels() {
        val manifest = RepositoryManifest(
            name = "Test Repo",
            description = "Test Description",
            pluginLists = listOf("plugins.json"),
            plugins = listOf(
                PluginItem(
                    internalName = "InlinePlugin",
                    name = "Inline Provider"
                )
            )
        )
        assertEquals("Test Repo", manifest.name)
        assertEquals("Test Description", manifest.description)
        assertEquals(1, manifest.pluginLists.size)
        assertEquals(1, manifest.plugins.size)
        assertEquals("InlinePlugin", manifest.plugins.first().internalName)
    }
}
