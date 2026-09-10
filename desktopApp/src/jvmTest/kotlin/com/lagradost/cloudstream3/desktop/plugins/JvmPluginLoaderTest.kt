package com.lagradost.cloudstream3.desktop.plugins

import com.lagradost.cloudstream3.APIHolder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
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

    @Test
    fun testRealDiskReconciliationWithPlugins() = kotlinx.coroutines.runBlocking {
        val userHome = System.getProperty("user.home") ?: "."
        val pluginsDir = File(userHome, ".cloudstream/plugins")
        if (!pluginsDir.exists() || (pluginsDir.listFiles()?.isEmpty() == true)) {
            println("No plugins directory found, skipping real disk test")
            return@runBlocking
        }

        val prefRepo = FakeDesktopPreferenceRepository()
        prefRepo.setString(com.lagradost.cloudstream3.shared.plugins.DefaultPluginsRepository.KEY_INSTALLED_PLUGINS, "[]")

        val repository = com.lagradost.cloudstream3.shared.plugins.DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginLoader = JvmPluginLoader()
        )
        val installed = repository.getInstalledPlugins()
        println("Reconciled installed count: ${installed.size}")
        assertTrue(installed.size >= 43, "Should have reconciled 43 plugins from user's disk, found: ${installed.size}")
        val saved: String? = prefRepo.getString(com.lagradost.cloudstream3.shared.plugins.DefaultPluginsRepository.KEY_INSTALLED_PLUGINS)
        kotlin.test.assertNotNull(saved)
        assertTrue(saved != "[]" && saved.contains("JKAnimeProvider"), "Preferences should be synchronized with healed disk plugins")
    }
}

private class FakeDesktopPreferenceRepository : com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository {
    private val data = mutableMapOf<String, String>()
    private val flows = mutableMapOf<String, kotlinx.coroutines.flow.MutableStateFlow<String?>>()

    override suspend fun getString(key: String, defaultValue: String?): String? = data[key] ?: defaultValue
    override fun getStringFlow(key: String): kotlinx.coroutines.flow.Flow<String?> = flows.getOrPut(key) { kotlinx.coroutines.flow.MutableStateFlow(data[key]) }
    override suspend fun setString(key: String, value: String) {
        data[key] = value
        flows.getOrPut(key) { kotlinx.coroutines.flow.MutableStateFlow(null) }.value = value
    }
    override suspend fun getInt(key: String, defaultValue: Int): Int = data[key]?.toIntOrNull() ?: defaultValue
    override suspend fun setInt(key: String, value: Int) { setString(key, value.toString()) }
    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = data[key]?.toBooleanStrictOrNull() ?: defaultValue
    override suspend fun setBoolean(key: String, value: Boolean) { setString(key, value.toString()) }
    override suspend fun getStringSet(key: String, defaultValue: Set<String>?): kotlinx.collections.immutable.ImmutableSet<String>? =
        (data[key]?.split(",")?.toSet() ?: defaultValue)?.toImmutableSet()
    override suspend fun setStringSet(key: String, value: Set<String>) { setString(key, value.joinToString(",")) }
    override suspend fun getKeys(prefix: String): kotlinx.collections.immutable.ImmutableList<String> =
        data.keys.filter { it.startsWith(prefix) }.toImmutableList()
    override suspend fun removeKeys(prefix: String): Int {
        val keys = data.keys.filter { it.startsWith(prefix) }.toList()
        keys.forEach { deletePreference(it) }
        return keys.size
    }
    override suspend fun deletePreference(key: String) {
        data.remove(key)
        flows[key]?.value = null
    }
    override suspend fun clearAll() {
        data.clear()
        flows.values.forEach { it.value = null }
    }
    override fun getStringSync(key: String, defaultValue: String?): String? = data[key] ?: defaultValue
    override fun getIntSync(key: String, defaultValue: Int): Int = data[key]?.toIntOrNull() ?: defaultValue
    override fun getBooleanSync(key: String, defaultValue: Boolean): Boolean = data[key]?.toBooleanStrictOrNull() ?: defaultValue
    override fun getStringSetSync(key: String, defaultValue: Set<String>?): kotlinx.collections.immutable.ImmutableSet<String>? =
        (data[key]?.split(",")?.toSet() ?: defaultValue)?.toImmutableSet()
    override fun setStringSync(key: String, value: String) { data[key] = value }
    override fun setIntSync(key: String, value: Int) { data[key] = value.toString() }
    override fun setBooleanSync(key: String, value: Boolean) { data[key] = value.toString() }
    override fun setStringSetSync(key: String, value: Set<String>) { data[key] = value.joinToString(",") }
    override fun deletePreferenceSync(key: String) { data.remove(key) }
    override fun getKeysSync(prefix: String): kotlinx.collections.immutable.ImmutableList<String> =
        data.keys.filter { it.startsWith(prefix) }.toImmutableList()
    override fun removeKeysSync(prefix: String): Int {
        val keys = data.keys.filter { it.startsWith(prefix) }.toList()
        keys.forEach { data.remove(it) }
        return keys.size
    }
    override fun getAllSync(): kotlinx.collections.immutable.ImmutableMap<String, String> =
        data.toImmutableMap()
}

