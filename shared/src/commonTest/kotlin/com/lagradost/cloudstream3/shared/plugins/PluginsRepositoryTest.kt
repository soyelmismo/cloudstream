package com.lagradost.cloudstream3.shared.plugins

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakeAppPreferenceRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakePluginManager(
    override val pluginsDirectory: File = File(System.getProperty("java.io.tmpdir"), "fake_empty_${System.nanoTime()}")
) : PluginManager {
    val installed = mutableListOf<PluginItem>()
    val uninstalledNames = mutableListOf<String>()

    override suspend fun downloadPlugin(url: String, targetFile: File): Result<File> {
        return Result.success(targetFile)
    }

    override suspend fun installPlugin(plugin: PluginItem): Result<PluginItem> {
        val updated = plugin.copy(isInstalled = true, isDownloaded = true)
        installed.removeAll { it.internalName == plugin.internalName }
        installed.add(updated)
        return Result.success(updated)
    }

    override suspend fun uninstallPlugin(filenameOrName: String): Result<Unit> {
        uninstalledNames.add(filenameOrName)
        val clean = filenameOrName.removeSuffix(".cs3").removeSuffix(".jar")
        installed.removeAll {
            it.internalName == filenameOrName ||
                it.name == filenameOrName ||
                it.internalName.equals(clean, ignoreCase = true)
        }
        return Result.success(Unit)
    }

    override fun isPluginLoaded(name: String): Boolean = true
}

class PluginsRepositoryTest {
    private val testJson = Json { ignoreUnknownKeys = true }

    @Test
    fun testAddAndGetRepositories() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        assertTrue(repository.getRepositories().isEmpty())

        val repo1 = PluginRepositoryItem(name = "Repo One", url = "https://example.com/repo1.json")
        repository.addRepository(repo1)

        val repos = repository.getRepositories()
        assertEquals(1, repos.size)
        assertEquals("Repo One", repos.first().name)
        assertEquals("https://example.com/repo1.json", repos.first().url)

        repository.removeRepository(repo1.url)
        assertTrue(repository.getRepositories().isEmpty())
    }

    @Test
    fun testLiveFetchListoClick() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        val plugins = repository.getAvailablePlugins(
            listOf(PluginRepositoryItem(name = "Stormunblessed providers repository", url = "https://c.listo.click"))
        )
        println("Fetched plugins count: ${plugins.size}")
        for (p in plugins.take(3)) {
            println("Plugin: name=${p.name}, url=${p.url}, repo=${p.repositoryUrl}")
        }
        assertTrue(plugins.isNotEmpty(), "Plugins from c.listo.click should not be empty")
    }

    @Test
    fun testInstallAndUninstallPluginsPersistsInPreferences() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        val plugin = PluginItem(
            internalName = "ProviderA",
            name = "Provider A",
            version = 1,
            repositoryUrl = "https://example.com/repo.json"
        )

        val installResult = repository.installPlugin(plugin)
        assertTrue(installResult.isSuccess)

        val installedList = repository.getInstalledPlugins()
        assertEquals(1, installedList.size)
        assertEquals("ProviderA", installedList.first().internalName)

        val uninstallResult = repository.uninstallPlugin("ProviderA.cs3")
        assertTrue(uninstallResult.isSuccess)

        val afterUninstall = repository.getInstalledPlugins()
        assertEquals(0, afterUninstall.size)
        assertTrue(pluginManager.uninstalledNames.contains("ProviderA.cs3"))
    }

    @Test
    fun testMergePluginWithInstalled() {
        val installed = PluginItem(
            internalName = "PluginX",
            name = "Plugin X",
            version = 1,
            description = null
        )
        val available = PluginItem(
            internalName = "PluginX",
            name = "Plugin X",
            version = 2,
            description = "Updated description",
            authors = listOf("Dev"),
            iconUrl = "https://example.com/icon.png"
        )

        val merged = mergePluginWithInstalled(installed, available)
        assertTrue(merged.hasUpdate)
        assertEquals(2, merged.remoteVersion)
        assertEquals("Updated description", merged.description)
        assertEquals(listOf("Dev"), merged.authors)
        assertEquals("https://example.com/icon.png", merged.iconUrl)
    }

    @Test
    fun testPluginFilterMatching() {
        val installed = PluginItem(
            internalName = "InstalledPlugin",
            name = "Installed",
            isInstalled = true,
            isDownloaded = true,
            hasUpdate = true,
            language = "en",
            tvTypes = listOf("Movie")
        )
        val available = PluginItem(
            internalName = "AvailablePlugin",
            name = "Available",
            isInstalled = false,
            isDownloaded = false,
            hasUpdate = false,
            language = "es",
            tvTypes = listOf("Anime")
        )

        assertTrue(matchesFilterMode(installed, PluginFilterMode.ALL))
        assertTrue(matchesFilterMode(installed, PluginFilterMode.INSTALLED))
        assertFalse(matchesFilterMode(installed, PluginFilterMode.AVAILABLE))
        assertTrue(matchesFilterMode(installed, PluginFilterMode.UPDATES_AVAILABLE))

        assertTrue(matchesFilterMode(available, PluginFilterMode.ALL))
        assertFalse(matchesFilterMode(available, PluginFilterMode.INSTALLED))
        assertTrue(matchesFilterMode(available, PluginFilterMode.AVAILABLE))
        assertFalse(matchesFilterMode(available, PluginFilterMode.UPDATES_AVAILABLE))

        assertTrue(matchesSearchQuery(installed, "install"))
        assertFalse(matchesSearchQuery(installed, "cartoon"))

        assertTrue(matchesLanguage(installed.language, "en"))
        assertTrue(matchesLanguage(installed.language, null))
        assertFalse(matchesLanguage(installed.language, "es"))

        assertTrue(matchesTvType(installed.tvTypes, "Movie"))
        assertTrue(matchesTvType(installed.tvTypes, null))
        assertFalse(matchesTvType(installed.tvTypes, "Anime"))

        assertTrue(
            matchesPlugin(
                plugin = installed,
                filterMode = PluginFilterMode.INSTALLED,
                searchQuery = "Installed",
                selectedLanguage = "en",
                selectedTvType = "Movie"
            )
        )
    }

    @Test
    fun testSitePluginParsingAndMapping() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        val rawJson = """
            [
                {
                    "url": "https://raw.githubusercontent.com/soyelmismo/storm-ext/builds/AllCalidadProvider.cs3",
                    "status": 1,
                    "version": 1,
                    "name": "AllCalidadProvider",
                    "internalName": "AllCalidadProvider",
                    "authors": ["redblacker8"],
                    "description": "Películas, series y animes online en HD.",
                    "fileSize": 35608,
                    "repositoryUrl": "https://github.com/soyelmismo/storm-ext",
                    "language": "mx",
                    "tvTypes": ["TvSeries", "Movie", "Anime"],
                    "iconUrl": "https://www.google.com/s2/favicons?domain=allcalidad.re",
                    "apiVersion": 1,
                    "fileHash": "sha256-1c97c377d4d0b2aec44e741675f509f40177f17dc5b36ef2d3e0bb7aa2d8bf52"
                },
                {
                    "url": "DisabledProvider.cs3",
                    "status": 0,
                    "version": 2,
                    "name": "DisabledProvider",
                    "internalName": "DisabledProvider"
                }
            ]
        """.trimIndent()

        val sitePlugins = json.decodeFromString<List<com.lagradost.cloudstream3.plugins.SitePlugin>>(rawJson)
        assertEquals(2, sitePlugins.size)

        val item1 = sitePlugins[0].toPluginItem("https://c.listo.click", "https://c.listo.click", false)
        assertEquals("AllCalidadProvider", item1.name)
        assertEquals("AllCalidadProvider", item1.internalName)
        assertEquals(1, item1.version)
        assertEquals("https://c.listo.click", item1.repositoryUrl)
        assertTrue(item1.isEnabled)
        assertFalse(item1.isInstalled)
        assertEquals(listOf("redblacker8"), item1.authors)
        assertEquals(listOf("TvSeries", "Movie", "Anime"), item1.tvTypes)
        assertEquals("https://raw.githubusercontent.com/soyelmismo/storm-ext/builds/AllCalidadProvider.cs3", item1.url)
        assertEquals(ProviderStatus.OK, item1.providerStatus)
        assertFalse(item1.isDown)
        assertTrue(item1.canInstall)
        assertTrue(item1.canEnable)

        val item2 = sitePlugins[1].toPluginItem("https://c.listo.click", "https://c.listo.click", true)
        assertEquals("DisabledProvider", item2.name)
        assertFalse(item2.isEnabled)
        assertTrue(item2.isInstalled)
        assertEquals("https://c.listo.click/DisabledProvider.cs3", item2.url)
        assertEquals(ProviderStatus.DOWN, item2.providerStatus)
        assertTrue(item2.isDown)
        assertFalse(item2.canInstall)
        assertFalse(item2.canEnable)
    }

    @Test
    fun testResolvePluginUrlRelativeAndAbsolute() {
        assertEquals(
            "https://c.listo.click/plugin.cs3",
            resolvePluginUrl("plugin.cs3", "https://c.listo.click")
        )
        assertEquals(
            "https://c.listo.click/plugin.cs3",
            resolvePluginUrl("/plugin.cs3", "https://c.listo.click/")
        )
        assertEquals(
            "https://example.com/other.cs3",
            resolvePluginUrl("https://example.com/other.cs3", "https://c.listo.click")
        )
    }

    @Test
    fun testLegacyInstalledPluginsMigrationFromBothKeys() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        prefRepo.setString(
            DefaultPluginsRepository.KEY_LEGACY_PLUGINS,
            """[{"internalName":"OnlinePlugin","url":"https://example.com/online.cs3","isOnline":true,"filePath":"","version":1}]"""
        )
        prefRepo.setString(
            DefaultPluginsRepository.KEY_LEGACY_PLUGINS_LOCAL,
            """[{"internalName":"LocalPlugin","url":null,"isOnline":false,"filePath":"/data/local.cs3","version":2}]"""
        )

        val installed = repository.getInstalledPlugins()
        assertEquals(2, installed.size)
        assertTrue(installed.any { it.internalName == "OnlinePlugin" })
        assertTrue(installed.any { it.internalName == "LocalPlugin" && it.localFilePath == "/data/local.cs3" })
    }

    @Test
    fun testCachedAvailablePluginsPruningOnRepositoryRemoval() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        val repo1 = PluginRepositoryItem(name = "Repo One", url = "https://example.com/repo1.json")
        val repo2 = PluginRepositoryItem(name = "Repo Two", url = "https://example.com/repo2.json")
        repository.addRepository(repo1)
        repository.addRepository(repo2)

        prefRepo.setString(
            DefaultPluginsRepository.KEY_CACHED_AVAILABLE_PLUGINS,
            """[
                {"internalName":"P1","name":"P1","repositoryUrl":"https://example.com/repo1.json"},
                {"internalName":"P2","name":"P2","repositoryUrl":"https://example.com/repo2.json"}
            ]"""
        )

        val cachedBefore = repository.getCachedAvailablePlugins()
        assertEquals(2, cachedBefore.size)

        repository.removeRepository("https://example.com/repo1.json/")
        val cachedAfter = repository.getCachedAvailablePlugins()
        assertEquals(1, cachedAfter.size)
        assertEquals("P2", cachedAfter.first().internalName)
    }

    @Test
    fun testNormalizeUrlKeyComparison() {
        val url1 = "https://c.listo.click/"
        val url2 = "HTTPS://C.LISTO.CLICK"
        val url3 = " https://c.listo.click "
        assertEquals(normalizeUrlKey(url1), normalizeUrlKey(url2))
        assertEquals(normalizeUrlKey(url1), normalizeUrlKey(url3))
    }

    @Test
    fun testAutoHealingOrphanDiskFilesReconciliation() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_autoheal_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val file1 = File(testPluginsDir, "AllCalidadProvider.cs3").apply { writeText("dummy content 1") }
            val file2 = File(testPluginsDir, "CustomSideload.cs3").apply { writeText("dummy content 2") }

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS, "[]")
            prefRepo.setString(
                DefaultPluginsRepository.KEY_CACHED_AVAILABLE_PLUGINS,
                """[
                    {
                        "internalName": "AllCalidadProvider",
                        "name": "AllCalidad Provider",
                        "version": 3,
                        "repositoryUrl": "https://example.com/repo.json",
                        "authors": ["AuthorX"],
                        "iconUrl": "https://example.com/icon.png"
                    }
                ]"""
            )

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val installed = repository.getInstalledPlugins()
            assertEquals(2, installed.size)

            val allCalidad = installed.find { it.internalName == "AllCalidadProvider" }
            assertNotNull(allCalidad)
            assertTrue(allCalidad.isInstalled)
            assertTrue(allCalidad.isDownloaded)
            assertEquals("https://example.com/repo.json", allCalidad.repositoryUrl)
            assertEquals(listOf("AuthorX"), allCalidad.authors)
            assertEquals("https://example.com/icon.png", allCalidad.iconUrl)
            assertEquals(file1.absolutePath, allCalidad.localFilePath)

            val sideload = installed.find { it.internalName == "CustomSideload" }
            assertNotNull(sideload)
            assertTrue(sideload.isInstalled)
            assertTrue(sideload.isDownloaded)
            assertEquals("", sideload.repositoryUrl)
            assertEquals("CustomSideload", sideload.name)
            assertEquals(file2.absolutePath, sideload.localFilePath)

            val savedPref = prefRepo.getString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS)
            assertNotNull(savedPref)
            assertTrue(savedPref.contains("AllCalidadProvider"))
            assertTrue(savedPref.contains("CustomSideload"))
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testReconciliationEnrichesMetadataOnAvailableSync() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_enrich_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            File(testPluginsDir, "LateSyncProvider.cs3").apply { writeText("dummy") }

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS, "[]")

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val initialInstalled = repository.getInstalledPlugins()
            assertEquals(1, initialInstalled.size)
            assertEquals("", initialInstalled.first().repositoryUrl)

            val freshAvailable = listOf(
                PluginItem(
                    internalName = "LateSyncProvider",
                    name = "Late Sync Provider",
                    version = 2,
                    repositoryUrl = "https://sync.org",
                    authors = listOf("SyncDev")
                )
            )
            val reconciled = repository.reconcileWithDisk(freshAvailable)
            assertEquals(1, reconciled.size)
            assertEquals("https://sync.org", reconciled.first().repositoryUrl)
            assertEquals(listOf("SyncDev"), reconciled.first().authors)
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testPluginManagerIsPluginLoadedVerification() {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_loaded_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            assertFalse(pluginManager.isPluginLoaded("NonExistentProvider"))

            val dummyProvider = object : MainAPI() {
                override var name = "DummyLoadedProvider"
                override var mainUrl = "https://dummy.org"
            }
            dummyProvider.sourcePlugin = File(testPluginsDir, "DummyLoadedProvider.cs3").absolutePath

            APIHolder.allProviders.withLock {
                APIHolder.allProviders.add(dummyProvider)
            }

            try {
                assertTrue(pluginManager.isPluginLoaded("DummyLoadedProvider"))
                assertTrue(pluginManager.isPluginLoaded("DummyLoadedProvider.cs3"))
                assertTrue(pluginManager.isPluginLoaded(dummyProvider.sourcePlugin!!))
            } finally {
                APIHolder.allProviders.withLock {
                    APIHolder.allProviders.remove(dummyProvider)
                }
            }
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testAutoPruningDeletedDiskFile() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_prune_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val fileActive = File(testPluginsDir, "ActiveProvider.cs3").apply { writeText("active") }
            val deletedPath = File(testPluginsDir, "DeletedProvider.cs3").absolutePath

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(
                DefaultPluginsRepository.KEY_INSTALLED_PLUGINS,
                """[
                    {"internalName":"ActiveProvider","name":"ActiveProvider","localFilePath":"${fileActive.absolutePath}","isInstalled":true},
                    {"internalName":"DeletedProvider","name":"DeletedProvider","localFilePath":"$deletedPath","isInstalled":true}
                ]"""
            )

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val installed = repository.getInstalledPlugins()
            assertEquals(1, installed.size)
            assertEquals("ActiveProvider", installed.first().internalName)

            val savedJson = prefRepo.getString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS)
            assertNotNull(savedJson)
            assertFalse(savedJson.contains("DeletedProvider"))
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testUninstallPluginDeletesFileOnDiskAndUpdatesPreferences() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_uninst_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val file = File(testPluginsDir, "AnimeflvProvider.cs3").apply { writeText("dummy") }
            assertTrue(file.exists())

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(
                DefaultPluginsRepository.KEY_INSTALLED_PLUGINS,
                """[
                    {"internalName":"AnimeflvProvider","name":"AnimeFLV","localFilePath":"${file.absolutePath}","isInstalled":true}
                ]"""
            )

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val uninstallResult = repository.uninstallPlugin("AnimeFLV")
            assertTrue(uninstallResult.isSuccess)
            assertFalse(file.exists(), "Physical file on disk should be deleted upon uninstallation")

            val afterUninstall = repository.getInstalledPlugins()
            assertEquals(0, afterUninstall.size)
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testDirectZipManifestExtractionWithoutPluginLoader() {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_manifest_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val cs3File = File(testPluginsDir, "CustomZipPlugin.cs3")
            java.util.zip.ZipOutputStream(cs3File.outputStream().buffered()).use { zos ->
                val entry = java.util.zip.ZipEntry("manifest.json")
                zos.putNextEntry(entry)
                zos.write("""{"name":"Custom Manifest Provider","version":7,"pluginClassName":"com.example.TestPlugin"}""".encodeToByteArray())
                zos.closeEntry()
            }

            val manager = DefaultPluginManager(pluginLoader = null, baseDirectory = testPluginsDir)
            val manifest = manager.getPluginManifest(cs3File)
            assertNotNull(manifest)
            assertEquals("Custom Manifest Provider", manifest.name)
            assertEquals(7, manifest.version)
            assertEquals("com.example.TestPlugin", manifest.pluginClassName)
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testInstalledPluginMatchingWithProviderSuffixDifferences() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_suffix_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            File(testPluginsDir, "AnimeflvProvider.cs3").apply { writeText("dummy") }

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS, "[]")

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val available = listOf(
                PluginItem(
                    internalName = "AnimeflvProvider",
                    name = "AnimeFLV",
                    version = 3,
                    repositoryUrl = "https://example.com/repo.json",
                    description = "Watch anime online"
                )
            )

            val reconciled = repository.reconcileWithDisk(available)
            assertEquals(1, reconciled.size)
            val item = reconciled.first()
            assertEquals("AnimeFLV", item.name)
            assertEquals("AnimeflvProvider", item.internalName)
            assertEquals("https://example.com/repo.json", item.repositoryUrl)
            assertEquals("Watch anime online", item.description)
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testAutoPruningWhenAllFilesDeletedAndDirectoryEmpty() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_empty_prune_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val nonExistentPath = File(testPluginsDir, "AlreadyDeleted.cs3").absolutePath
            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(
                DefaultPluginsRepository.KEY_INSTALLED_PLUGINS,
                """[{"internalName":"AlreadyDeleted","name":"AlreadyDeleted","localFilePath":"$nonExistentPath","isInstalled":true}]"""
            )

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val installed = repository.getInstalledPlugins()
            assertEquals(0, installed.size, "Missing disk files must be pruned even if directory has 0 files")

            val savedJson = prefRepo.getString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS)
            assertNotNull(savedJson)
            assertEquals("[]", savedJson)
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testMatchingDiskFileWithProviderSuffixAvoidsDuplicates() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_dup_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val file = File(testPluginsDir, "AnimeflvProvider.cs3").apply { writeText("dummy") }

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(
                DefaultPluginsRepository.KEY_INSTALLED_PLUGINS,
                """[{"internalName":"AnimeFLV","name":"AnimeFLV","isInstalled":true}]"""
            )

            val pluginManager = DefaultPluginManager(baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val installed = repository.getInstalledPlugins()
            assertEquals(1, installed.size, "Must not create duplicate entries for AnimeFLV vs AnimeflvProvider.cs3")
            val item = installed.first()
            assertEquals(file.absolutePath, item.localFilePath)
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testDiskManifestVersionPreservedAndHasUpdateCalculated() = runTest {
        val testPluginsDir = File(System.getProperty("java.io.tmpdir"), "test_ver_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val cs3File = File(testPluginsDir, "VersionedProvider.cs3")
            java.util.zip.ZipOutputStream(cs3File.outputStream().buffered()).use { zos ->
                val entry = java.util.zip.ZipEntry("manifest.json")
                zos.putNextEntry(entry)
                zos.write("""{"name":"Versioned Provider","version":2,"pluginClassName":"com.example.Versioned"}""".encodeToByteArray())
                zos.closeEntry()
            }

            val prefRepo = FakeAppPreferenceRepository()
            prefRepo.setString(DefaultPluginsRepository.KEY_INSTALLED_PLUGINS, "[]")

            val pluginManager = DefaultPluginManager(pluginLoader = null, baseDirectory = testPluginsDir)
            val repository = DefaultPluginsRepository(
                preferenceRepository = prefRepo,
                pluginManager = pluginManager
            )

            val available = listOf(
                PluginItem(
                    internalName = "VersionedProvider",
                    name = "Versioned Provider",
                    version = 4,
                    repositoryUrl = "https://example.com/repo.json"
                )
            )

            val reconciled = repository.reconcileWithDisk(available)
            assertEquals(1, reconciled.size)
            val item = reconciled.first()
            assertEquals(2, item.version, "Installed version must reflect disk manifest version")
            assertEquals(4, item.remoteVersion, "Remote version must reflect repository version")
            assertTrue(item.hasUpdate, "hasUpdate must be true when repository version > disk version")
        } finally {
            testPluginsDir.deleteRecursively()
        }
    }

    @Test
    fun testLanguageAndTvTypeMatchingWithAllAndBlank() {
        val plugin = PluginItem(
            internalName = "TestPlugin",
            name = "Test",
            language = "en",
            tvTypes = listOf("Movie", "TvSeries")
        )

        assertTrue(matchesLanguage(plugin.language, null))
        assertTrue(matchesLanguage(plugin.language, ""))
        assertTrue(matchesLanguage(plugin.language, "all"))
        assertTrue(matchesLanguage(plugin.language, "ALL"))
        assertTrue(matchesLanguage(plugin.language, "en"))
        assertFalse(matchesLanguage(plugin.language, "es"))

        assertTrue(matchesTvType(plugin.tvTypes, null))
        assertTrue(matchesTvType(plugin.tvTypes, ""))
        assertTrue(matchesTvType(plugin.tvTypes, "all"))
        assertTrue(matchesTvType(plugin.tvTypes, "ALL"))
        assertTrue(matchesTvType(plugin.tvTypes, "Movie"))
        assertFalse(matchesTvType(plugin.tvTypes, "Anime"))
    }

    @Test
    fun testStatusSerializationVariants() {
        val jsonString = """
            [
                {"name": "P1", "internalName": "P1", "status": 0},
                {"name": "P2", "internalName": "P2", "status": "down"},
                {"name": "P3", "internalName": "P3", "status": "disabled"},
                {"name": "P4", "internalName": "P4", "status": "broken"},
                {"name": "P5", "internalName": "P5", "status": 2},
                {"name": "P6", "internalName": "P6", "status": "slow"},
                {"name": "P7", "internalName": "P7", "status": 3},
                {"name": "P8", "internalName": "P8", "status": "beta"},
                {"name": "P9", "internalName": "P9", "status": 1},
                {"name": "P10", "internalName": "P10", "status": "ok"}
            ]
        """.trimIndent()

        val parsed = testJson.decodeFromString<List<com.lagradost.cloudstream3.plugins.SitePlugin>>(jsonString)
        assertEquals(10, parsed.size)
        assertEquals(0, parsed[0].status)
        assertEquals(0, parsed[1].status)
        assertEquals(0, parsed[2].status)
        assertEquals(0, parsed[3].status)
        assertEquals(2, parsed[4].status)
        assertEquals(2, parsed[5].status)
        assertEquals(3, parsed[6].status)
        assertEquals(3, parsed[7].status)
        assertEquals(1, parsed[8].status)
        assertEquals(1, parsed[9].status)

        val itemDown = parsed[0].toPluginItem("https://example.com", "https://example.com", false)
        assertTrue(itemDown.isDown)
        assertFalse(itemDown.canInstall)
        assertFalse(itemDown.canEnable)
        assertFalse(itemDown.isEnabled)

        val itemSlow = parsed[4].toPluginItem("https://example.com", "https://example.com", false)
        assertEquals(ProviderStatus.SLOW, itemSlow.providerStatus)
        assertFalse(itemSlow.isDown)
        assertTrue(itemSlow.canInstall)
    }

    @Test
    fun testMergePluginWithInstalledWhenAvailableIsDown() {
        val installed = PluginItem(
            internalName = "AnimeflvProvider",
            name = "AnimeflvProvider",
            version = 5,
            isInstalled = true,
            isDownloaded = true,
            isEnabled = true,
            providerStatus = ProviderStatus.OK
        )

        val available = PluginItem(
            internalName = "AnimeflvProvider",
            name = "AnimeflvProvider",
            version = 6,
            isInstalled = false,
            isDownloaded = false,
            isEnabled = false,
            providerStatus = ProviderStatus.DOWN
        )

        val merged = mergePluginWithInstalled(installed, available)
        assertEquals(ProviderStatus.DOWN, merged.providerStatus)
        assertFalse(merged.isEnabled, "Installed plugin must be disabled when available is down")
        assertTrue(merged.isDown)
        assertFalse(merged.canEnable)
        assertFalse(merged.canInstall)
        assertTrue(merged.hasUpdate)
        assertEquals(6, merged.remoteVersion)
    }

    @Test
    fun testInstallDownPluginRejectedInRepository() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        val downPlugin = PluginItem(
            internalName = "AnimeflvProvider",
            name = "AnimeflvProvider",
            version = 6,
            providerStatus = ProviderStatus.DOWN
        )

        val result = repository.installPlugin(downPlugin)
        assertTrue(result.isFailure, "Installing a DOWN plugin must fail in repository")
        assertTrue(repository.getInstalledPlugins().isEmpty(), "No plugins should be installed")
    }

    @Test
    fun testReconcileWithDiskDisablesInstalledDownPlugin() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        val pluginManager = FakePluginManager()
        val repository = DefaultPluginsRepository(
            preferenceRepository = prefRepo,
            pluginManager = pluginManager
        )

        // Install a healthy plugin initially
        val healthyPlugin = PluginItem(
            internalName = "AnimeflvProvider",
            name = "AnimeflvProvider",
            version = 5,
            providerStatus = ProviderStatus.OK,
            isEnabled = true
        )
        val installRes = repository.installPlugin(healthyPlugin)
        assertTrue(installRes.isSuccess)

        // Remote repository now flags it as DOWN
        val availablePool = listOf(
            PluginItem(
                internalName = "AnimeflvProvider",
                name = "AnimeflvProvider",
                version = 6,
                providerStatus = ProviderStatus.DOWN,
                isEnabled = false
            )
        )

        val reconciled = repository.reconcileWithDisk(availablePool)
        assertEquals(1, reconciled.size)
        val reconciledItem = reconciled.first()
        assertEquals(ProviderStatus.DOWN, reconciledItem.providerStatus)
        assertFalse(reconciledItem.isEnabled, "Reconciled plugin must be disabled when remote is down")
        assertTrue(reconciledItem.isDown)
    }
}


