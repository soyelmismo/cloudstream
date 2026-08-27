package com.lagradost.cloudstream3.desktop.plugins

import com.lagradost.cloudstream3.APIHolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmPluginLoaderTest {

    @Test
    fun testLoadInstalledPlugin() {
        val userHome = System.getProperty("user.home") ?: "."
        val pluginFile = File(userHome, ".cloudstream/plugins/AnimeflvProvider.cs3")
        if (!pluginFile.exists()) {
            println("AnimeflvProvider.cs3 not present, skipping test")
            return
        }

        val loader = JvmPluginLoader()
        val plugin = loader.loadPlugin(pluginFile.absolutePath)
        assertNotNull(plugin, "Plugin should be successfully loaded")

        val allApis = APIHolder.apis.toList() + APIHolder.allProviders.toList()
        println("Loaded APIs: ${allApis.map { it.name }}")
        assertTrue(allApis.isNotEmpty(), "APIHolder should have at least one registered provider")
    }

    @Test
    fun testJKAnimePluginAbi() {
        val userHome = System.getProperty("user.home") ?: "."
        val pluginFile = File(userHome, ".cloudstream/plugins/JKAnimeProvider.cs3")
        if (!pluginFile.exists()) {
            println("JKAnimeProvider.cs3 not present, skipping test")
            return
        }

        val loader = JvmPluginLoader()
        val plugin = loader.loadPlugin(pluginFile.absolutePath)
        assertNotNull(plugin, "JKAnimeProvider should load")

        val provider = APIHolder.apis.firstOrNull { it.name.contains("JKAnime", ignoreCase = true) }
            ?: APIHolder.allProviders.firstOrNull { it.name.contains("JKAnime", ignoreCase = true) }

        assertNotNull(provider, "JKAnime provider should be registered")

        // Test invoking load to ensure Result.constructor-impl method is resolved properly
        kotlinx.coroutines.runBlocking {
            try {
                provider.load("https://jkanime.net/one-piece/")
            } catch (t: Throwable) {
                // If it fails with network timeout or similar, that's fine, but NoSuchMethodError is a failure
                if (t is NoSuchMethodError) {
                    throw t
                }
            }
        }
    }
}
