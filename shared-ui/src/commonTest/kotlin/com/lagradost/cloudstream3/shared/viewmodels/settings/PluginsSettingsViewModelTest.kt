package com.lagradost.cloudstream3.shared.viewmodels.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.lagradost.cloudstream3.APIHolder
import cloudstream.shared_ui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakePluginsRepository(
    var failingPlugins: Set<String> = emptySet(),
    var shouldFailAll: Boolean = false
) : PluginsRepository {
    private val repositories = mutableListOf(
        PluginRepositoryItem("CloudStream Community", "https://example.com/community.json", isRemovable = false),
        PluginRepositoryItem("Hexated Repository", "https://example.com/hexated.json", isRemovable = true)
    )
    private val installedPlugins = mutableListOf<PluginItem>()
    private val availablePlugins = mutableListOf(
        PluginItem(
            internalName = "CommunityPlugin",
            name = "Community Provider",
            version = 1,
            url = "https://example.com/plugin.cs3",
            repositoryUrl = "https://example.com/community.json",
            tvTypes = listOf("Anime", "Movie"),
            language = "en"
        ),
        PluginItem(
            internalName = "StreamPlugin",
            name = "Stream Provider",
            version = 1,
            url = "https://example.com/stream.cs3",
            repositoryUrl = "https://example.com/community.json",
            tvTypes = listOf("Live"),
            language = "en"
        ),
        PluginItem(
            internalName = "HexatedPlugin",
            name = "Hexated Provider",
            version = 1,
            url = "https://example.com/hexated.cs3",
            repositoryUrl = "https://example.com/hexated.json",
            tvTypes = listOf("Cartoon"),
            language = "es"
        )
    )

    override suspend fun getRepositories(): List<PluginRepositoryItem> = repositories.toList()

    override suspend fun addRepository(repository: PluginRepositoryItem) {
        repositories.add(repository)
    }

    override suspend fun removeRepository(url: String) {
        repositories.removeAll { it.url == url }
    }

    override suspend fun getInstalledPlugins(): List<PluginItem> = installedPlugins.toList()

    override suspend fun getAvailablePlugins(repositories: List<PluginRepositoryItem>): List<PluginItem> {
        val installedNames = installedPlugins.map { it.internalName }.toSet()
        return availablePlugins.map { it.copy(isInstalled = installedNames.contains(it.internalName)) }
    }

    override suspend fun installPlugin(plugin: PluginItem): Result<PluginItem> {
        if (shouldFailAll || failingPlugins.contains(plugin.internalName)) {
            return Result.failure(Exception("Failed to install ${plugin.name}"))
        }
        val installed = plugin.copy(isInstalled = true, localFilePath = "/plugins/${plugin.internalName}.cs3")
        installedPlugins.removeAll { it.internalName == plugin.internalName }
        installedPlugins.add(installed)
        return Result.success(installed)
    }

    override suspend fun uninstallPlugin(filenameOrName: String): Result<Unit> {
        if (shouldFailAll || failingPlugins.contains(filenameOrName)) {
            return Result.failure(Exception("Failed to uninstall $filenameOrName"))
        }
        installedPlugins.removeAll { it.internalName == filenameOrName || it.name == filenameOrName }
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PluginsSettingsViewModelTest {

    @Test
    fun testInitialLoadAndRepositories() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.repositories.isNotEmpty())
        assertEquals(2, state.repositories.size)
        assertTrue(state.availablePlugins.isNotEmpty())
        assertTrue(state.installedPlugins.isEmpty())
    }

    @Test
    fun testAddAndRemoveRepository() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val initialCount = viewModel.state.value.repositories.size

        viewModel.onEvent(
            PluginsSettingsEvent.AddRepository(
                url = "https://example.com/custom-repo/repo.json",
                name = "My Custom Repo"
            )
        )
        testScope.advanceUntilIdle()
        assertEquals(initialCount + 1, viewModel.state.value.repositories.size)
        assertTrue(viewModel.state.value.repositories.any { it.url == "https://example.com/custom-repo/repo.json" })

        viewModel.onEvent(
            PluginsSettingsEvent.RemoveRepository(
                url = "https://example.com/custom-repo/repo.json"
            )
        )
        testScope.advanceUntilIdle()

        assertEquals(initialCount, viewModel.state.value.repositories.size)
        assertFalse(viewModel.state.value.repositories.any { it.url == "https://example.com/custom-repo/repo.json" })
    }

    @Test
    fun testInstallAndUninstallPlugin() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val pluginToInstall = viewModel.state.value.availablePlugins.first()
        viewModel.onEvent(PluginsSettingsEvent.InstallPlugin(pluginToInstall))
        testScope.advanceUntilIdle()

        assertTrue(viewModel.state.value.installedPlugins.any { it.internalName == pluginToInstall.internalName })
        val installedInAvailable = viewModel.state.value.availablePlugins.first { it.internalName == pluginToInstall.internalName }
        assertTrue(installedInAvailable.isInstalled)
        assertTrue(viewModel.state.value.operationState is PluginOperationState.Success)
        val installSuccess = viewModel.state.value.operationState as PluginOperationState.Success
        assertEquals(Res.string.plugin_installed_success, installSuccess.messageRes)
        assertEquals(listOf(pluginToInstall.name), installSuccess.formatArgs)

        viewModel.onEvent(PluginsSettingsEvent.UninstallPlugin(pluginToInstall.internalName))
        testScope.advanceUntilIdle()

        assertFalse(viewModel.state.value.installedPlugins.any { it.internalName == pluginToInstall.internalName })
        val uninstalledInAvailable = viewModel.state.value.availablePlugins.first { it.internalName == pluginToInstall.internalName }
        assertFalse(uninstalledInAvailable.isInstalled)
        assertTrue(viewModel.state.value.operationState is PluginOperationState.Success)
        val uninstallSuccess = viewModel.state.value.operationState as PluginOperationState.Success
        assertEquals(Res.string.plugin_uninstalled_success, uninstallSuccess.messageRes)
        assertEquals(listOf(pluginToInstall.internalName), uninstallSuccess.formatArgs)
    }

    @Test
    fun testSearchAndFiltering() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        // Filter by TV Type
        viewModel.onEvent(PluginsSettingsEvent.FilterByTvType("Anime"))
        testScope.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.filteredAvailablePlugins.size)

        // Filter by non-existent TV Type
        viewModel.onEvent(PluginsSettingsEvent.FilterByTvType("NonExistentType"))
        testScope.advanceUntilIdle()
        assertEquals(0, viewModel.state.value.filteredAvailablePlugins.size)

        // Reset TV Type filter & test search
        viewModel.onEvent(PluginsSettingsEvent.FilterByTvType(null))
        viewModel.onEvent(PluginsSettingsEvent.Search("Community"))
        testScope.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.filteredAvailablePlugins.size)
        assertTrue(viewModel.state.value.filteredAvailablePlugins.first().name.contains("Community"))
    }

    @Test
    fun testRepositoryStatsAndSync() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.repositories.size)
        // Check stats populated
        state.repositories.forEach { repo ->
            assertTrue(repo.lastSyncTime != null && repo.lastSyncTime > 0L)
        }

        // Test SyncRepositories event
        viewModel.onEvent(PluginsSettingsEvent.SyncRepositories)
        testScope.advanceUntilIdle()

        val syncedState = viewModel.state.value
        assertEquals(2, syncedState.repositories.size)
        assertFalse(syncedState.isLoading)
    }

    @Test
    fun testUrlValidationAndNormalization() {
        // Validation
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("https://raw.githubusercontent.com/user/repo/builds/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("http://example.com/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("cloudstreamrepo://raw.githubusercontent.com/user/repo/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("cs3-repo://raw.githubusercontent.com/user/repo/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("https://cs.repo/?https://example.com/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("raw.githubusercontent.com/user/repo/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("github.com/user/repo/repo.json"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("https://github.com/user/repo"))
        assertTrue(PluginsSettingsViewModel.isValidRepoUrl("!shortcode"))

        assertFalse(PluginsSettingsViewModel.isValidRepoUrl(""))
        assertFalse(PluginsSettingsViewModel.isValidRepoUrl("   "))
        assertFalse(PluginsSettingsViewModel.isValidRepoUrl("invalid-not-url"))
        assertFalse(PluginsSettingsViewModel.isValidRepoUrl("ftp://invalid.com/repo.json"))
        assertFalse(PluginsSettingsViewModel.isValidRepoUrl("https://bad url with spaces.json"))

        // Normalization
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("cloudstreamrepo://raw.githubusercontent.com/user/repo/repo.json")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/builds/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("cs3-repo://raw.githubusercontent.com/user/repo/builds/repo.json")
        )
        assertEquals(
            "https://example.com/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("https://cs.repo/?https://example.com/repo.json")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("raw.githubusercontent.com/user/repo/repo.json")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/builds/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("https://github.com/user/repo")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/builds/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("https://github.com/user/repo/")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/builds/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("https://github.com/user/repo/builds")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/builds/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("https://github.com/user/repo/blob/builds/repo.json")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/builds/repo.json",
            PluginsSettingsViewModel.normalizeRepoUrl("https://raw.githubusercontent.com/user/repo/builds")
        )
        assertEquals(
            "https://py.md/myshortcode",
            PluginsSettingsViewModel.normalizeRepoUrl("!myshortcode")
        )
    }

    @Test
    fun testFormatSyncTime() {
        assertEquals("Never", PluginsSettingsViewModel.formatSyncTime(null))
        assertEquals("Never", PluginsSettingsViewModel.formatSyncTime(0L))

        val now = APIHolder.unixTimeMS
        assertEquals("Just now", PluginsSettingsViewModel.formatSyncTime(now))
        assertEquals("Just now", PluginsSettingsViewModel.formatSyncTime(now - 10_000L))
        assertEquals("5 min ago", PluginsSettingsViewModel.formatSyncTime(now - 5 * 60 * 1000L))
        assertEquals("3 hr ago", PluginsSettingsViewModel.formatSyncTime(now - 3 * 3600 * 1000L))
        assertEquals("Yesterday", PluginsSettingsViewModel.formatSyncTime(now - 24 * 3600 * 1000L))
        assertEquals("4 days ago", PluginsSettingsViewModel.formatSyncTime(now - 4 * 24 * 3600 * 1000L))
    }

    @Test
    fun testAddInvalidRepositoryUrl() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val initialCount = viewModel.state.value.repositories.size

        // Add invalid URL
        viewModel.onEvent(PluginsSettingsEvent.AddRepository(url = "not a valid url", name = "Invalid Repo"))
        testScope.advanceUntilIdle()

        assertEquals(initialCount, viewModel.state.value.repositories.size)
        assertTrue(viewModel.state.value.error != null)
    }

    @Test
    fun testPluginDetailsAndProperties() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val availablePlugin = viewModel.state.value.availablePlugins.first()
        val detailedPlugin = availablePlugin.copy(
            changelog = "Added multiplatform support\nFixed stream extraction",
            permissions = listOf("INTERNET", "STORAGE"),
            remoteVersion = 2
        )

        // Verify PluginDetails destination
        val screenDestination = com.lagradost.cloudstream3.shared.ui.Screen.PluginDetails(detailedPlugin)
        assertEquals(detailedPlugin.name, screenDestination.plugin.name)
        assertEquals(detailedPlugin.internalName, screenDestination.plugin.internalName)
        assertEquals(2, detailedPlugin.remoteVersion)
        assertEquals(2, detailedPlugin.permissions.size)
        assertTrue(detailedPlugin.changelog!!.contains("multiplatform"))

        // Install and verify state
        viewModel.onEvent(PluginsSettingsEvent.InstallPlugin(detailedPlugin))
        testScope.advanceUntilIdle()

        val installedPlugin = viewModel.state.value.installedPlugins.first { it.internalName == detailedPlugin.internalName }
        assertTrue(installedPlugin.isInstalled)

        // Toggle disabled / enabled
        val disabledPlugin = installedPlugin.copy(isEnabled = false)
        viewModel.onEvent(PluginsSettingsEvent.InstallPlugin(disabledPlugin))
        testScope.advanceUntilIdle()

        val updatedPlugin = viewModel.state.value.installedPlugins.first { it.internalName == detailedPlugin.internalName }
        assertFalse(updatedPlugin.isEnabled)
    }

    @Test
    fun testDefaultPluginsRepositorySavedOnly() = runTest {
        val prefRepo = FakeAppPreferenceRepository()

        val defaultRepo = DefaultPluginsRepository(preferenceRepository = prefRepo)

        // Initially empty
        assertTrue(DefaultPluginsRepository.PREBUILT_REPOSITORIES.isEmpty())
        assertTrue(defaultRepo.getRepositories().isEmpty())

        // Add repository
        val testRepo = PluginRepositoryItem(
            name = "Test Repo",
            url = "https://example.com/repo.json",
            description = "Test Description"
        )
        defaultRepo.addRepository(testRepo)

        val stored = defaultRepo.getRepositories()
        assertEquals(1, stored.size)
        assertEquals("Test Repo", stored.first().name)
        assertEquals("https://example.com/repo.json", stored.first().url)

        // Remove repository
        defaultRepo.removeRepository("https://example.com/repo.json")
        assertTrue(defaultRepo.getRepositories().isEmpty())
    }

    @Test
    fun testHierarchicalNavigationAndRepoHelpers() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val initialState = viewModel.state.value
        // Root / All repos view: selectedRepository is null, currentRepoPlugins contains all plugins
        assertEquals(null, initialState.selectedRepositoryUrl)
        assertEquals(null, initialState.selectedRepository)
        assertEquals(3, initialState.currentRepoPlugins.size)

        // Select Community Repository (Level 2)
        viewModel.onEvent(PluginsSettingsEvent.FilterByRepository("https://example.com/community.json"))
        testScope.advanceUntilIdle()

        val communityState = viewModel.state.value
        assertEquals("https://example.com/community.json", communityState.selectedRepositoryUrl)
        assertEquals("CloudStream Community", communityState.selectedRepository?.name)
        assertEquals(2, communityState.currentRepoPlugins.size)
        assertTrue(communityState.currentRepoPlugins.all { it.repositoryUrl == "https://example.com/community.json" })

        // Select Hexated Repository (Level 2)
        viewModel.onEvent(PluginsSettingsEvent.FilterByRepository("https://example.com/hexated.json"))
        testScope.advanceUntilIdle()

        val hexatedState = viewModel.state.value
        assertEquals("https://example.com/hexated.json", hexatedState.selectedRepositoryUrl)
        assertEquals("Hexated Repository", hexatedState.selectedRepository?.name)
        assertEquals(1, hexatedState.currentRepoPlugins.size)
        assertEquals("HexatedPlugin", hexatedState.currentRepoPlugins.first().internalName)

        // Clear repo selection (back to Level 1)
        viewModel.onEvent(PluginsSettingsEvent.FilterByRepository(null))
        testScope.advanceUntilIdle()

        val clearedState = viewModel.state.value
        assertEquals(null, clearedState.selectedRepositoryUrl)
        assertEquals(null, clearedState.selectedRepository)
        assertEquals(3, clearedState.currentRepoPlugins.size)
    }

    @Test
    fun testSelectPluginForDetails() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val targetPlugin = viewModel.state.value.availablePlugins.first()
        assertEquals(null, viewModel.state.value.selectedPluginForDetails)

        // Select plugin
        viewModel.onEvent(PluginsSettingsEvent.SelectPluginForDetails(targetPlugin))
        testScope.advanceUntilIdle()
        assertEquals(targetPlugin.internalName, viewModel.state.value.selectedPluginForDetails?.internalName)

        // Clear selected plugin
        viewModel.onEvent(PluginsSettingsEvent.SelectPluginForDetails(null))
        testScope.advanceUntilIdle()
        assertEquals(null, viewModel.state.value.selectedPluginForDetails)
    }

    @Test
    fun testBatchInstallAndUninstallAllPlugins() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val repoUrl = "https://example.com/community.json"

        // Initially no plugins are installed
        assertEquals(0, viewModel.state.value.installedPlugins.size)

        // Batch install all plugins for Community repository
        viewModel.onEvent(PluginsSettingsEvent.InstallAllPlugins(repoUrl))
        testScope.advanceUntilIdle()

        val installedState = viewModel.state.value
        assertTrue(installedState.operationState is PluginOperationState.Success)
        val batchInstallSuccess = installedState.operationState
        assertEquals(Res.string.batch_install_success, batchInstallSuccess.messageRes)
        assertEquals(listOf(2), batchInstallSuccess.formatArgs)
        assertEquals(2, installedState.installedPlugins.size)
        assertTrue(installedState.installedPlugins.all { it.repositoryUrl == repoUrl })
        assertTrue(installedState.availablePlugins.filter { it.repositoryUrl == repoUrl }.all { it.isInstalled })
        // Hexated plugin should still not be installed
        val hexatedPlugin = installedState.availablePlugins.first { it.repositoryUrl == "https://example.com/hexated.json" }
        assertFalse(hexatedPlugin.isInstalled)

        // Batch uninstall all plugins for Community repository
        viewModel.onEvent(PluginsSettingsEvent.UninstallAllPlugins(repoUrl))
        testScope.advanceUntilIdle()

        val uninstalledState = viewModel.state.value
        assertTrue(uninstalledState.operationState is PluginOperationState.Success)
        val batchUninstallSuccess = uninstalledState.operationState
        assertEquals(Res.string.batch_uninstall_success, batchUninstallSuccess.messageRes)
        assertEquals(listOf(2), batchUninstallSuccess.formatArgs)
        assertEquals(0, uninstalledState.installedPlugins.size)
        assertTrue(uninstalledState.availablePlugins.filter { it.repositoryUrl == repoUrl }.none { it.isInstalled })
    }

    @Test
    fun testBatchInstallWithFailures() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository(failingPlugins = setOf("CommunityPlugin"))

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val repoUrl = "https://example.com/community.json"

        // Batch install with 1 failure out of 2
        viewModel.onEvent(PluginsSettingsEvent.InstallAllPlugins(repoUrl))
        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.operationState is PluginOperationState.Error)
        val opError = state.operationState
        assertEquals(Res.string.batch_operation_failed, opError.messageRes)
        assertEquals(listOf(1), opError.formatArgs)
        assertEquals(1, state.installedPlugins.size)
        assertEquals("StreamPlugin", state.installedPlugins.first().internalName)
    }

    @Test
    fun testBatchUninstallWithFailures() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository(failingPlugins = setOf("CommunityPlugin"))

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val repoUrl = "https://example.com/community.json"

        // First install StreamPlugin cleanly (not failing)
        viewModel.onEvent(PluginsSettingsEvent.InstallPlugin(viewModel.state.value.availablePlugins.first { it.internalName == "StreamPlugin" }))
        testScope.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.installedPlugins.size)

        // Force CommunityPlugin to simulate installed
        val commPlugin = viewModel.state.value.availablePlugins.first { it.internalName == "CommunityPlugin" }
        repository.installPlugin(commPlugin.copy(internalName = "TempCommunityPlugin")) // to have another installed if needed
        testScope.advanceUntilIdle()

        // Batch uninstall
        viewModel.onEvent(PluginsSettingsEvent.UninstallAllPlugins(repoUrl))
        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        // Only StreamPlugin was installed in state, and it uninstalls successfully
        assertTrue(state.operationState is PluginOperationState.Success)
    }

    @Test
    fun testBatchOperationEmptyTargets() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakePluginsRepository()

        val viewModel = PluginsSettingsViewModel(
            pluginsRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        // Uninstall when nothing is installed -> should set Idle
        viewModel.onEvent(PluginsSettingsEvent.UninstallAllPlugins("https://example.com/community.json"))
        testScope.advanceUntilIdle()

        assertEquals(PluginOperationState.Idle, viewModel.state.value.operationState)
    }
}

