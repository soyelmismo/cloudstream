package com.lagradost.cloudstream3.shared.ui.settings

import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.SubtitleStyle
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.shared.sync.manager.CloudSyncProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import org.jetbrains.compose.resources.stringResource
import com.lagradost.cloudstream4.generated.resources.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.AuthPinData
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.syncproviders.SubtitleRepo
import com.lagradost.cloudstream3.shared.ui.components.auth.ProviderAccountDialog
import com.lagradost.cloudstream3.shared.ui.components.auth.ProviderLoginDialog
import com.lagradost.cloudstream3.shared.ui.components.auth.ProviderOAuthDialog
import com.lagradost.cloudstream3.shared.ui.components.auth.ProviderPinDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.shared.ui.components.DohSelector
import com.lagradost.cloudstream3.shared.ui.components.ResponsiveSettingsScaffold
import com.lagradost.cloudstream3.shared.ui.components.SettingsCard
import com.lagradost.cloudstream3.shared.ui.components.SettingsCategory
import com.lagradost.cloudstream3.shared.ui.components.SettingsChoiceDialog
import com.lagradost.cloudstream3.shared.ui.components.SettingsItemRow
import com.lagradost.cloudstream3.shared.ui.components.SettingsSectionHeader
import com.lagradost.cloudstream3.shared.ui.components.SettingsSwitchItem
import com.lagradost.cloudstream3.shared.ui.components.SubtitleCustomizer
import com.lagradost.cloudstream3.shared.ui.components.ThemeSelector
import com.lagradost.cloudstream3.shared.ui.plugins.PluginsScreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.lagradost.cloudstream3.shared.backup.BackupCategory
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsState
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main Settings Screen connecting AppSettingsViewModel and PluginsSettingsViewModel.
 * Supports responsive dual-pane layout for Desktop JVM and single-pane navigation for Mobile.
 */
@Composable
fun SettingsScreen(
    appSettingsViewModel: AppSettingsViewModel,
    pluginsViewModel: PluginsSettingsViewModel? = null,
    onBackClick: (() -> Unit)? = null,
    onNavigateToAccountSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appState by appSettingsViewModel.state.collectAsState()
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val titleAppearance = stringResource(Res.string.sectionAppearance)
    val descAppearance = stringResource(Res.string.sectionAppearanceDesc)
    val titlePlayerSubtitles = stringResource(Res.string.sectionPlayerSubtitles)
    val descPlayerSubtitles = stringResource(Res.string.sectionPlayerSubtitlesDesc)
    val titleSyncAccounts = stringResource(Res.string.sectionSyncAccounts)
    val descSyncAccounts = stringResource(Res.string.sectionSyncAccountsDesc)
    val titleBackupRestore = stringResource(Res.string.sectionBackupRestore)
    val descBackupRestore = stringResource(Res.string.sectionBackupRestoreDesc)
    val titleNetworkDns = stringResource(Res.string.sectionNetworkDns)
    val descNetworkDns = stringResource(Res.string.sectionNetworkDnsDesc)
    val titlePlugins = stringResource(Res.string.sectionPlugins)
    val descPlugins = stringResource(Res.string.sectionPluginsDesc)
    val titleGeneral = stringResource(Res.string.sectionGeneral)
    val descGeneral = stringResource(Res.string.sectionGeneralDesc)

    val categories = remember(
        titleAppearance, descAppearance,
        titlePlayerSubtitles, descPlayerSubtitles,
        titleSyncAccounts, descSyncAccounts,
        titleBackupRestore, descBackupRestore,
        titleNetworkDns, descNetworkDns,
        titlePlugins, descPlugins,
        titleGeneral, descGeneral
    ) {
        persistentListOf(
            SettingsCategory(
                id = "appearance",
                title = titleAppearance,
                description = descAppearance,
                icon = Icons.Default.Palette
            ),
            SettingsCategory(
                id = "player_subtitles",
                title = titlePlayerSubtitles,
                description = descPlayerSubtitles,
                icon = Icons.Default.Subtitles
            ),
            SettingsCategory(
                id = "sync_accounts",
                title = titleSyncAccounts,
                description = descSyncAccounts,
                icon = Icons.Default.Person
            ),
            SettingsCategory(
                id = "backup_restore",
                title = titleBackupRestore,
                description = descBackupRestore,
                icon = Icons.Default.RestartAlt
            ),
            SettingsCategory(
                id = "network_dns",
                title = titleNetworkDns,
                description = descNetworkDns,
                icon = Icons.Default.Dns
            ),
            SettingsCategory(
                id = "plugins",
                title = titlePlugins,
                description = descPlugins,
                icon = Icons.Default.Extension
            ),
            SettingsCategory(
                id = "general",
                title = titleGeneral,
                description = descGeneral,
                icon = Icons.Default.Tune
            )
        )
    }

    ResponsiveSettingsScaffold(
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        onSelectCategory = { selectedCategoryId = it },
        topBarTitle = stringResource(Res.string.settingsTitle),
        onBackClick = onBackClick,
        modifier = modifier
    ) { currentCategory ->
        when (currentCategory.id) {
            "appearance" -> AppearanceSettingsSection(
                state = appState,
                onThemeSelected = appSettingsViewModel::setTheme,
                onDarkModeChanged = appSettingsViewModel::setDarkMode,
                onOpenLanguageDialog = { showLanguageDialog = true }
            )
            "player_subtitles" -> PlayerSubtitlesSettingsSection(
                state = appState,
                onQualityWifiChanged = appSettingsViewModel::setQualityWifi,
                onQualityMobileChanged = appSettingsViewModel::setQualityMobile,
                onSoftwareDecodingChanged = appSettingsViewModel::setSoftwareDecoding,
                onSubtitleEncodingChanged = appSettingsViewModel::setSubtitleEncoding,
                onShowSourcesOnPlayChanged = appSettingsViewModel::setShowSourcesOnPlay,
                onDefaultSubtitleStyleChanged = appSettingsViewModel::setDefaultSubtitleStyle
            )
            "sync_accounts" -> SyncAccountsSettingsSection(
                state = appState,
                onStartOAuthLogin = appSettingsViewModel::startOAuthLogin,
                onSwitchActiveAccount = appSettingsViewModel::switchActiveAccount,
                onLogoutAccount = appSettingsViewModel::logoutAccount,
                onSyncWatchProgressChanged = appSettingsViewModel::setSyncWatchProgress,
                onSyncScoresChanged = appSettingsViewModel::setSyncScores,
                onSyncWifiOnlyChanged = appSettingsViewModel::setSyncWifiOnly,
                onSkipStartupAccountSelectChanged = appSettingsViewModel::setSkipStartupAccountSelect,
                onNavigateToAccountSelect = onNavigateToAccountSelect
            )
            "backup_restore" -> BackupRestoreSettingsSection(
                state = appState,
                onExportBackup = appSettingsViewModel::exportBackupWithPicker,
                onImportBackup = appSettingsViewModel::importBackupWithPicker,
                onClearBackupMessage = appSettingsViewModel::clearBackupMessage,
                onRequestReset = { showResetDialog = true },
                onProviderSelected = appSettingsViewModel::setCloudSyncProvider,
                onStartGoogleAuth = appSettingsViewModel::startGoogleDriveAuth,
                onCompleteGoogleAuth = appSettingsViewModel::completeGoogleDriveAuth,
                onCancelGoogleAuth = appSettingsViewModel::cancelGoogleDriveAuth,
                onStartOneDriveAuth = appSettingsViewModel::startOneDriveAuth,
                onCompleteOneDriveAuth = appSettingsViewModel::completeOneDriveAuth,
                onCancelOneDriveAuth = appSettingsViewModel::cancelOneDriveAuth,
                onDisconnectCloudSync = appSettingsViewModel::disconnectCloudSync,
                onSaveWebDavConfig = appSettingsViewModel::saveWebDavConfig,
                onSaveS3Config = appSettingsViewModel::saveS3Config,
                onSaveLocalPath = appSettingsViewModel::saveLocalSyncPath,
                onSyncNow = appSettingsViewModel::runCloudSyncNow,
                onTestConnection = appSettingsViewModel::testCloudSyncConnection,
                onAutoSyncToggled = appSettingsViewModel::toggleCloudAutoSync
            )
            "network_dns" -> NetworkDnsSettingsSection(
                state = appState,
                onDohProviderSelected = appSettingsViewModel::setDohProvider
            )
            "plugins" -> {
                if (pluginsViewModel != null) {
                    PluginsScreen(
                        viewModel = pluginsViewModel
                    )
                } else {
                    PluginsFallbackNotice()
                }
            }
            "general" -> GeneralSettingsSection(
                state = appState,
                onSyncWatchProgressChanged = appSettingsViewModel::setSyncWatchProgress,
                onSyncScoresChanged = appSettingsViewModel::setSyncScores,
                onSyncWifiOnlyChanged = appSettingsViewModel::setSyncWifiOnly,
                onSkipStartupAccountSelectChanged = appSettingsViewModel::setSkipStartupAccountSelect,
                onRequestReset = { showResetDialog = true },
                onNavigateToAccountSelect = onNavigateToAccountSelect
            )
        }
    }

    if (showResetDialog) {
        ConfirmDeleteDialog(
            onConfirm = {
                appSettingsViewModel.resetToDefaults()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
            titleRes = Res.string.resetConfirmTitle,
            messageRes = Res.string.resetConfirmDesc,
            confirmTextRes = Res.string.resetToDefaultsButton
        )
    }

    if (showLanguageDialog) {
        val formatPattern = stringResource(Res.string.lang_code_format)
        val supportedLanguages = listOf(
            "en" to "English (US)",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "pt" to "Português",
            "it" to "Italiano",
            "ar" to "العربية",
            "hi" to "हिन्दी",
            "ja" to "日本語",
            "zh" to "中文"
        )

        SettingsChoiceDialog(
            title = stringResource(Res.string.selectAppLanguage),
            items = supportedLanguages,
            selectedItem = supportedLanguages.firstOrNull { it.first.equals(appState.appLanguage, ignoreCase = true) } ?: supportedLanguages.first(),
            itemLabel = { it.second },
            itemSubtitle = { formatPattern.replace("%s", it.first) },
            onItemSelected = {
                appSettingsViewModel.setAppLanguage(it.first)
                showLanguageDialog = false
            },
            onDismissRequest = { showLanguageDialog = false }
        )
    }
}

@Composable
fun AppearanceSettingsSection(
    state: AppSettingsState,
    onThemeSelected: (AppTheme) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onOpenLanguageDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.theme),
                icon = Icons.Default.Palette
            )

            Spacer(modifier = Modifier.height(8.dp))

            ThemeSelector(
                selectedTheme = state.theme,
                onThemeSelected = onThemeSelected,
                isDarkMode = state.isDarkMode
            )
        }

        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.sectionAppearance),
                icon = Icons.Default.Brightness4
            )

            SettingsSwitchItem(
                title = stringResource(Res.string.darkMode),
                subtitle = stringResource(Res.string.darkModeDesc),
                checked = state.isDarkMode,
                onCheckedChange = onDarkModeChanged
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            SettingsItemRow(
                title = stringResource(Res.string.appLanguage),
                subtitle = stringResource(Res.string.appLanguageDesc),
                valueText = state.appLanguage.uppercase(),
                icon = Icons.Default.Language,
                onClick = onOpenLanguageDialog
            )
        }
    }
}

@Composable
fun PlayerSubtitlesSettingsSection(
    state: AppSettingsState,
    onQualityWifiChanged: (Int) -> Unit,
    onQualityMobileChanged: (Int) -> Unit,
    onSoftwareDecodingChanged: (Int) -> Unit,
    onSubtitleEncodingChanged: (String) -> Unit,
    onShowSourcesOnPlayChanged: (Boolean) -> Unit,
    onDefaultSubtitleStyleChanged: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWifiQualityDialog by remember { mutableStateOf(false) }
    var showMobileQualityDialog by remember { mutableStateOf(false) }
    var showSoftwareDecodingDialog by remember { mutableStateOf(false) }
    var showSubtitleEncodingDialog by remember { mutableStateOf(false) }

    val autoText = stringResource(Res.string.quality_auto)
    val autoDecodingText = stringResource(Res.string.automatic)

    val qualityOptions = remember(autoText) {
        listOf(
            0 to autoText,
            2160 to "2160p (4K)",
            1440 to "1440p (HD)",
            1080 to "1080p (HD)",
            720 to "720p (HD)",
            480 to "480p (SD)",
            360 to "360p (SD)",
            240 to "240p (SD)"
        )
    }

    val softwareDecodingOptions = remember(autoDecodingText) {
        listOf(
            -1 to autoDecodingText,
            0 to "Hardware & Software",
            1 to "Hardware Only",
            2 to "Software Preferred"
        )
    }

    val subtitleEncodings = remember {
        listOf(
            "UTF-8",
            "UTF-16",
            "ISO-8859-1",
            "Windows-1252",
            "Windows-1256",
            "GBK",
            "Big5",
            "Shift_JIS",
            "EUC-KR"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.video_quality),
                icon = Icons.Default.Tune
            )

            SettingsItemRow(
                title = stringResource(Res.string.watch_quality_pref),
                subtitle = stringResource(Res.string.video_quality),
                valueText = qualityOptions.firstOrNull { it.first == state.qualityWifi }?.second ?: autoText,
                onClick = { showWifiQualityDialog = true }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            SettingsItemRow(
                title = stringResource(Res.string.watch_quality_pref_data),
                subtitle = stringResource(Res.string.video_quality),
                valueText = qualityOptions.firstOrNull { it.first == state.qualityMobile }?.second ?: autoText,
                onClick = { showMobileQualityDialog = true }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            SettingsItemRow(
                title = stringResource(Res.string.software_decoding),
                subtitle = stringResource(Res.string.software_decoding_desc),
                valueText = softwareDecodingOptions.firstOrNull { it.first == state.softwareDecoding }?.second ?: autoDecodingText,
                onClick = { showSoftwareDecodingDialog = true }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            SettingsSwitchItem(
                title = stringResource(Res.string.view_sources_on_play),
                subtitle = stringResource(Res.string.view_sources_on_play_summary),
                checked = state.showSourcesOnPlay,
                onCheckedChange = onShowSourcesOnPlayChanged
            )
        }

        SubtitleCustomizer(
            style = state.subtitleStyle,
            onStyleChanged = onDefaultSubtitleStyleChanged
        )

        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.pref_category_subtitles),
                icon = Icons.Default.Subtitles
            )

            SettingsItemRow(
                title = stringResource(Res.string.subtitles_encoding),
                subtitle = stringResource(Res.string.subtitles_encoding),
                valueText = state.subtitleEncoding,
                onClick = { showSubtitleEncodingDialog = true }
            )
        }
    }

    if (showWifiQualityDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.watch_quality_pref),
            items = qualityOptions,
            selectedItem = qualityOptions.firstOrNull { it.first == state.qualityWifi } ?: qualityOptions.first(),
            itemLabel = { it.second },
            onItemSelected = {
                onQualityWifiChanged(it.first)
                showWifiQualityDialog = false
            },
            onDismissRequest = { showWifiQualityDialog = false }
        )
    }

    if (showMobileQualityDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.watch_quality_pref_data),
            items = qualityOptions,
            selectedItem = qualityOptions.firstOrNull { it.first == state.qualityMobile } ?: qualityOptions.first(),
            itemLabel = { it.second },
            onItemSelected = {
                onQualityMobileChanged(it.first)
                showMobileQualityDialog = false
            },
            onDismissRequest = { showMobileQualityDialog = false }
        )
    }

    if (showSoftwareDecodingDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.software_decoding),
            items = softwareDecodingOptions,
            selectedItem = softwareDecodingOptions.firstOrNull { it.first == state.softwareDecoding } ?: softwareDecodingOptions.first(),
            itemLabel = { it.second },
            onItemSelected = {
                onSoftwareDecodingChanged(it.first)
                showSoftwareDecodingDialog = false
            },
            onDismissRequest = { showSoftwareDecodingDialog = false }
        )
    }

    if (showSubtitleEncodingDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.subtitles_encoding),
            items = subtitleEncodings,
            selectedItem = subtitleEncodings.firstOrNull { it.equals(state.subtitleEncoding, ignoreCase = true) } ?: subtitleEncodings.first(),
            itemLabel = { it },
            onItemSelected = {
                onSubtitleEncodingChanged(it)
                showSubtitleEncodingDialog = false
            },
            onDismissRequest = { showSubtitleEncodingDialog = false }
        )
    }
}

@Composable
fun SyncAccountsSettingsSection(
    state: AppSettingsState,
    onStartOAuthLogin: (AuthRepo) -> Unit,
    onSwitchActiveAccount: (AuthRepo, Int) -> Unit,
    onLogoutAccount: (AuthRepo, AuthUser) -> Unit,
    onSyncWatchProgressChanged: (Boolean) -> Unit,
    onSyncScoresChanged: (Boolean) -> Unit,
    onSyncWifiOnlyChanged: (Boolean) -> Unit,
    onSkipStartupAccountSelectChanged: (Boolean) -> Unit,
    onNavigateToAccountSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val syncProviders = remember { AccountManager.syncApis.filter { it.requiresLogin }.toImmutableList() }
    val subtitleProviders = remember { AccountManager.subtitleProviders.toImmutableList() }

    var activeAccountDialogRepo by remember { mutableStateOf<AuthRepo?>(null) }
    var activeLoginDialogRepo by remember { mutableStateOf<AuthRepo?>(null) }
    var activePinDialogRepo by remember { mutableStateOf<AuthRepo?>(null) }
    var activePinData by remember { mutableStateOf<AuthPinData?>(null) }
    var activeOAuthDialogRepo by remember { mutableStateOf<AuthRepo?>(null) }
    var activeOAuthUrl by remember { mutableStateOf<String?>(null) }
    var activeInfoDialogRepo by remember { mutableStateOf<AuthRepo?>(null) }

    var isPinLoading by remember { mutableStateOf(false) }
    var isLoginLoading by remember { mutableStateOf(false) }
    var isOAuthLoading by remember { mutableStateOf(false) }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var oauthErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activePinData) {
        val pinData = activePinData ?: return@LaunchedEffect
        val repo = activePinDialogRepo ?: return@LaunchedEffect
        val interval = (pinData.interval.coerceAtLeast(3)).toLong() * 1000L
        while (isActive) {
            delay(interval)
            try {
                val success = repo.login(pinData)
                if (success) {
                    activePinDialogRepo = null
                    activePinData = null
                    break
                }
            } catch (t: Throwable) {
                if (t.message?.contains("expired", ignoreCase = true) == true) {
                    pinErrorMessage = t.message
                    break
                }
            }
        }
    }

    val onProviderClick: (AuthRepo) -> Unit = { repo ->
        val authUser = state.activeAuthAccounts[repo.idPrefix]?.user
        if (authUser != null) {
            activeAccountDialogRepo = repo
        } else if (repo.hasPin) {
            pinErrorMessage = null
            isPinLoading = true
            activePinDialogRepo = repo
            coroutineScope.launch {
                try {
                    activePinData = repo.pinRequest()
                } catch (t: Throwable) {
                    pinErrorMessage = t.message
                } finally {
                    isPinLoading = false
                }
            }
        } else if (repo.hasOAuth2) {
            val page = repo.api.loginRequest()
            if (page != null) {
                activeOAuthDialogRepo = repo
                activeOAuthUrl = page.url
                oauthErrorMessage = null
                isOAuthLoading = false
                try {
                    uriHandler.openUri(page.url)
                } catch (_: Throwable) {
                    repo.openOAuth2Page()
                }
            } else {
                onStartOAuthLogin(repo)
            }
        } else if (repo.hasInApp) {
            loginErrorMessage = null
            isLoginLoading = false
            activeLoginDialogRepo = repo
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SyncTrackingProvidersCard(
            providers = syncProviders,
            state = state,
            onProviderClick = onProviderClick
        )

        SyncSubtitleProvidersCard(
            providers = subtitleProviders,
            state = state,
            onProviderClick = onProviderClick,
            onOpenInfo = { activeInfoDialogRepo = it }
        )

        SyncPreferencesCard(
            state = state,
            onSyncWatchProgressChanged = onSyncWatchProgressChanged,
            onSyncScoresChanged = onSyncScoresChanged,
            onSyncWifiOnlyChanged = onSyncWifiOnlyChanged,
            onSkipStartupAccountSelectChanged = onSkipStartupAccountSelectChanged
        )
    }

    SyncAuthDialogsHost(
        state = state,
        onSwitchActiveAccount = onSwitchActiveAccount,
        onLogoutAccount = onLogoutAccount,
        activeAccountDialogRepo = activeAccountDialogRepo,
        onDismissAccountDialog = { activeAccountDialogRepo = null },
        onOpenAddAccountFlow = { targetRepo ->
            activeAccountDialogRepo = null
            onProviderClick(targetRepo)
        },
        activeLoginDialogRepo = activeLoginDialogRepo,
        isLoginLoading = isLoginLoading,
        loginErrorMessage = loginErrorMessage,
        onDismissLoginDialog = {
            activeLoginDialogRepo = null
            loginErrorMessage = null
            isLoginLoading = false
        },
        onSubmitLogin = { formResponse ->
            val repo = activeLoginDialogRepo ?: return@SyncAuthDialogsHost
            coroutineScope.launch {
                isLoginLoading = true
                loginErrorMessage = null
                try {
                    val success = repo.login(formResponse)
                    if (success) {
                        activeLoginDialogRepo = null
                    } else {
                        loginErrorMessage = "Authentication failed"
                    }
                } catch (t: Throwable) {
                    loginErrorMessage = t.message ?: "Authentication failed"
                } finally {
                    isLoginLoading = false
                }
            }
        },
        activePinDialogRepo = activePinDialogRepo,
        activePinData = activePinData,
        isPinLoading = isPinLoading,
        pinErrorMessage = pinErrorMessage,
        onDismissPinDialog = {
            activePinDialogRepo = null
            activePinData = null
            pinErrorMessage = null
            isPinLoading = false
        },
        activeOAuthDialogRepo = activeOAuthDialogRepo,
        activeOAuthUrl = activeOAuthUrl,
        isOAuthLoading = isOAuthLoading,
        oauthErrorMessage = oauthErrorMessage,
        onDismissOAuthDialog = {
            activeOAuthDialogRepo = null
            activeOAuthUrl = null
            oauthErrorMessage = null
            isOAuthLoading = false
        },
        onSubmitOAuth = { redirectUrlOrToken ->
            val repo = activeOAuthDialogRepo ?: return@SyncAuthDialogsHost
            isOAuthLoading = true
            oauthErrorMessage = null
            coroutineScope.launch {
                try {
                    val success = repo.login(redirectUrlOrToken)
                    if (success) {
                        activeOAuthDialogRepo = null
                        activeOAuthUrl = null
                        oauthErrorMessage = null
                    } else {
                        oauthErrorMessage = "Failed to authenticate"
                    }
                } catch (t: Throwable) {
                    oauthErrorMessage = t.message ?: "Authentication failed"
                } finally {
                    isOAuthLoading = false
                }
            }
        },
        activeInfoDialogRepo = activeInfoDialogRepo,
        onDismissInfoDialog = { activeInfoDialogRepo = null }
    )
}

@Composable
private fun SyncTrackingProvidersCard(
    providers: ImmutableList<AuthRepo>,
    state: AppSettingsState,
    onProviderClick: (AuthRepo) -> Unit
) {
    SettingsCard {
        SettingsSectionHeader(
            title = stringResource(Res.string.sync_category_tracking),
            description = stringResource(Res.string.sync_category_tracking_desc),
            icon = Icons.Default.Person,
            iconTint = MaterialTheme.colors.primary
        )

        providers.forEachIndexed { index, repo ->
            val authUser = state.activeAuthAccounts[repo.idPrefix]?.user
            SettingsItemRow(
                title = repo.name,
                subtitle = if (authUser != null) {
                    stringResource(Res.string.sync_logged_in_as, authUser.name ?: "")
                } else {
                    stringResource(Res.string.sync_not_connected)
                },
                onClick = { onProviderClick(repo) }
            )

            if (index < providers.lastIndex) {
                Divider(color = CloudstreamTheme.extendedColors.divider)
            }
        }
    }
}

@Composable
private fun SyncSubtitleProvidersCard(
    providers: ImmutableList<SubtitleRepo>,
    state: AppSettingsState,
    onProviderClick: (AuthRepo) -> Unit,
    onOpenInfo: (SubtitleRepo) -> Unit
) {
    SettingsCard {
        SettingsSectionHeader(
            title = stringResource(Res.string.sync_category_subtitles),
            description = stringResource(Res.string.sync_category_subtitles_desc),
            icon = Icons.Default.Subtitles,
            iconTint = MaterialTheme.colors.secondary
        )

        providers.forEachIndexed { index, repo ->
            val authUser = state.activeAuthAccounts[repo.idPrefix]?.user
            SettingsItemRow(
                title = repo.name,
                subtitle = if (repo.requiresLogin) {
                    if (authUser != null) {
                        stringResource(Res.string.sync_logged_in_as, authUser.name ?: "")
                    } else {
                        stringResource(Res.string.sync_not_connected)
                    }
                } else {
                    stringResource(Res.string.sync_no_account_needed)
                },
                onClick = {
                    if (repo.requiresLogin) {
                        onProviderClick(repo)
                    } else {
                        onOpenInfo(repo)
                    }
                }
            )

            if (index < providers.lastIndex) {
                Divider(color = CloudstreamTheme.extendedColors.divider)
            }
        }
    }
}

@Composable
private fun SyncPreferencesCard(
    state: AppSettingsState,
    onSyncWatchProgressChanged: (Boolean) -> Unit,
    onSyncScoresChanged: (Boolean) -> Unit,
    onSyncWifiOnlyChanged: (Boolean) -> Unit,
    onSkipStartupAccountSelectChanged: (Boolean) -> Unit
) {
    SettingsCard {
        SettingsSectionHeader(
            title = stringResource(Res.string.sync_category_preferences),
            icon = Icons.Default.Tune,
            iconTint = MaterialTheme.colors.primary
        )

        SettingsSwitchItem(
            title = stringResource(Res.string.sync_watch_progress),
            subtitle = stringResource(Res.string.sync_watch_progress_desc),
            checked = state.syncWatchProgress,
            onCheckedChange = onSyncWatchProgressChanged
        )

        Divider(color = CloudstreamTheme.extendedColors.divider)

        SettingsSwitchItem(
            title = stringResource(Res.string.sync_scores),
            subtitle = stringResource(Res.string.sync_scores_desc),
            checked = state.syncScores,
            onCheckedChange = onSyncScoresChanged
        )

        Divider(color = CloudstreamTheme.extendedColors.divider)

        SettingsSwitchItem(
            title = stringResource(Res.string.sync_wifi_only),
            subtitle = stringResource(Res.string.sync_wifi_only_desc),
            checked = state.syncWifiOnly,
            onCheckedChange = onSyncWifiOnlyChanged
        )

        Divider(color = CloudstreamTheme.extendedColors.divider)

        SettingsSwitchItem(
            title = stringResource(Res.string.skip_startup_account_select_pref),
            subtitle = stringResource(Res.string.skip_startup_account_select_desc),
            checked = state.skipStartupAccountSelect,
            onCheckedChange = onSkipStartupAccountSelectChanged
        )
    }
}

@Composable
private fun SyncAuthDialogsHost(
    state: AppSettingsState,
    onSwitchActiveAccount: (AuthRepo, Int) -> Unit,
    onLogoutAccount: (AuthRepo, AuthUser) -> Unit,
    activeAccountDialogRepo: AuthRepo?,
    onDismissAccountDialog: () -> Unit,
    onOpenAddAccountFlow: (AuthRepo) -> Unit,
    activeLoginDialogRepo: AuthRepo?,
    isLoginLoading: Boolean,
    loginErrorMessage: String?,
    onDismissLoginDialog: () -> Unit,
    onSubmitLogin: (AuthLoginResponse) -> Unit,
    activePinDialogRepo: AuthRepo?,
    activePinData: AuthPinData?,
    isPinLoading: Boolean,
    pinErrorMessage: String?,
    onDismissPinDialog: () -> Unit,
    activeOAuthDialogRepo: AuthRepo?,
    activeOAuthUrl: String?,
    isOAuthLoading: Boolean,
    oauthErrorMessage: String?,
    onDismissOAuthDialog: () -> Unit,
    onSubmitOAuth: (String) -> Unit,
    activeInfoDialogRepo: AuthRepo?,
    onDismissInfoDialog: () -> Unit
) {
    if (activeAccountDialogRepo != null) {
        val currentAuth = state.activeAuthAccounts[activeAccountDialogRepo.idPrefix]
        ProviderAccountDialog(
            providerName = activeAccountDialogRepo.name,
            providerIcon = activeAccountDialogRepo.icon,
            currentUser = currentAuth?.user,
            accounts = activeAccountDialogRepo.accounts.toList(),
            onSelectAccount = { data ->
                onSwitchActiveAccount(activeAccountDialogRepo, data.user.id)
            },
            onAddAccount = { onOpenAddAccountFlow(activeAccountDialogRepo) },
            onLogout = { user ->
                onLogoutAccount(activeAccountDialogRepo, user)
                onDismissAccountDialog()
            },
            onDismiss = onDismissAccountDialog
        )
    }

    if (activeLoginDialogRepo != null) {
        ProviderLoginDialog(
            api = activeLoginDialogRepo.api,
            isLoading = isLoginLoading,
            errorMessage = loginErrorMessage,
            onLogin = onSubmitLogin,
            onDismiss = onDismissLoginDialog,
            onCreateAccount = { url ->
                AuthRepo.openBrowserHandler?.invoke(url)
            }
        )
    }

    if (activePinDialogRepo != null) {
        if (activePinData != null) {
            ProviderPinDialog(
                api = activePinDialogRepo.api,
                pinData = activePinData,
                isVerifying = true,
                errorMessage = pinErrorMessage,
                onDismiss = onDismissPinDialog,
                onOpenUrl = { url ->
                    AuthRepo.openBrowserHandler?.invoke(url)
                }
            )
        } else if (isPinLoading || pinErrorMessage != null) {
            ActionDialog(
                onDismissRequest = onDismissPinDialog,
                title = activePinDialogRepo.name,
                iconVector = Icons.Default.Person,
                iconTint = providerBrandColor(activePinDialogRepo.idPrefix),
                message = pinErrorMessage ?: stringResource(Res.string.auth_waiting_for_pin),
                cancelTextRes = Res.string.cancel,
                onCancel = onDismissPinDialog
            )
        }
    }

    if (activeOAuthDialogRepo != null && activeOAuthUrl != null) {
        ProviderOAuthDialog(
            repo = activeOAuthDialogRepo,
            authUrl = activeOAuthUrl,
            isLoading = isOAuthLoading,
            errorMessage = oauthErrorMessage,
            onCompleteLogin = onSubmitOAuth,
            onDismiss = onDismissOAuthDialog
        )
    }

    if (activeInfoDialogRepo != null) {
        val siteUrl = activeInfoDialogRepo.createAccountUrl ?: activeInfoDialogRepo.api.createAccountUrl
        ActionDialog(
            onDismissRequest = onDismissInfoDialog,
            title = activeInfoDialogRepo.name,
            iconVector = Icons.Default.Subtitles,
            iconTint = MaterialTheme.colors.secondary,
            messageRes = Res.string.sync_no_account_needed,
            confirmTextRes = if (siteUrl != null) Res.string.open_in_browser else null,
            onConfirm = if (siteUrl != null) {
                {
                    AuthRepo.openBrowserHandler?.invoke(siteUrl)
                    onDismissInfoDialog()
                }
            } else null,
            cancelTextRes = Res.string.close,
            onCancel = onDismissInfoDialog
        )
    }
}

@Composable
private fun providerBrandColor(idPrefix: String): Color = when (idPrefix) {
    AccountManager.aniListApi.idPrefix -> CloudStreamColors.BrandAniList
    AccountManager.malApi.idPrefix -> CloudStreamColors.BrandMyAnimeList
    AccountManager.simklApi.idPrefix -> CloudStreamColors.BrandSimkl
    AccountManager.kitsuApi.idPrefix -> CloudStreamColors.BrandKitsu
    AccountManager.openSubtitlesApi.idPrefix -> CloudStreamColors.BrandOpenSubtitles
    AccountManager.subDlApi.idPrefix -> CloudStreamColors.BrandSubdl
    AccountManager.addic7ed.idPrefix -> CloudStreamColors.BrandAddic7ed
    AccountManager.subSourceApi.idPrefix -> CloudStreamColors.BrandSubSource
    else -> CloudStreamColors.Primary
}

private fun backupCategoryIcon(category: BackupCategory): ImageVector = when (category) {
    BackupCategory.SETTINGS -> Icons.Default.Settings
    BackupCategory.WATCH_PROGRESS -> Icons.Default.History
    BackupCategory.BOOKMARKS -> Icons.Default.Bookmark
    BackupCategory.PLUGINS -> Icons.Default.Extension
    BackupCategory.SYNC_ACCOUNTS -> Icons.Default.Person
}

@Composable
fun BackupRestoreSettingsSection(
    state: AppSettingsState,
    onExportBackup: (Set<BackupCategory>) -> Unit,
    onImportBackup: () -> Unit,
    onClearBackupMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestReset: (() -> Unit)? = null,
    onProviderSelected: (CloudSyncProvider) -> Unit = {},
    onStartGoogleAuth: () -> Unit = {},
    onCompleteGoogleAuth: (String) -> Unit = {},
    onCancelGoogleAuth: () -> Unit = {},
    onStartOneDriveAuth: () -> Unit = {},
    onCompleteOneDriveAuth: (String) -> Unit = {},
    onCancelOneDriveAuth: () -> Unit = {},
    onDisconnectCloudSync: () -> Unit = {},
    onSaveWebDavConfig: (url: String, username: String, pass: String) -> Unit = { _, _, _ -> },
    onSaveS3Config: (endpoint: String, bucket: String, accessKey: String, secretKey: String, region: String) -> Unit = { _, _, _, _, _ -> },
    onSaveLocalPath: (path: String) -> Unit = {},
    onSyncNow: () -> Unit = {},
    onTestConnection: () -> Unit = {},
    onAutoSyncToggled: (Boolean) -> Unit = {}
) {
    var showBackupDialog by remember { mutableStateOf(false) }
    var selectedBackupCategories by remember {
        mutableStateOf(BackupCategory.entries.toSet())
    }

    var autoBackupEnabled by remember { mutableStateOf(true) }
    val defaultFrequency = stringResource(Res.string.backup_frequency_weekly)
    var autoBackupFrequency by remember { mutableStateOf(defaultFrequency) }
    var showFrequencyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BackupActionsCard(
            state = state,
            onOpenBackupDialog = {
                selectedBackupCategories = BackupCategory.entries.toSet()
                showBackupDialog = true
            },
            onRestoreClick = onImportBackup
        )

        AutoBackupCard(
            autoBackupEnabled = autoBackupEnabled,
            onAutoBackupEnabledChange = { autoBackupEnabled = it },
            autoBackupFrequency = autoBackupFrequency,
            onOpenFrequencyDialog = { showFrequencyDialog = true }
        )

        CloudSyncCard(
            state = state.cloudSync,
            isTestingConnection = state.isTestingCloudConnection,
            testConnectionResult = state.cloudConnectionTestResult,
            pendingOAuthUrl = state.pendingOAuthUrl,
            pendingOneDriveOAuthUrl = state.pendingOneDriveOAuthUrl,
            onProviderSelected = onProviderSelected,
            onStartGoogleAuth = onStartGoogleAuth,
            onCompleteGoogleAuth = onCompleteGoogleAuth,
            onCancelGoogleAuth = onCancelGoogleAuth,
            onStartOneDriveAuth = onStartOneDriveAuth,
            onCompleteOneDriveAuth = onCompleteOneDriveAuth,
            onCancelOneDriveAuth = onCancelOneDriveAuth,
            onDisconnect = onDisconnectCloudSync,
            onSaveWebDavConfig = onSaveWebDavConfig,
            onSaveS3Config = onSaveS3Config,
            onSaveLocalPath = onSaveLocalPath,
            onSyncNow = onSyncNow,
            onTestConnection = onTestConnection,
            onAutoSyncToggled = onAutoSyncToggled
        )
    }

    BackupCategorySelectionDialog(
        show = showBackupDialog,
        isBackingUp = state.isBackingUp,
        selectedCategories = selectedBackupCategories,
        onToggleCategory = { category ->
            selectedBackupCategories = if (selectedBackupCategories.contains(category)) {
                selectedBackupCategories - category
            } else {
                selectedBackupCategories + category
            }
        },
        onConfirm = {
            onExportBackup(selectedBackupCategories)
            showBackupDialog = false
        },
        onDismiss = {
            if (!state.isBackingUp) showBackupDialog = false
        }
    )

    BackupStatusDialogs(
        state = state,
        onDismiss = onClearBackupMessage
    )

    BackupFrequencyDialog(
        show = showFrequencyDialog,
        selectedFrequency = autoBackupFrequency,
        onSelectFrequency = {
            autoBackupFrequency = it
            showFrequencyDialog = false
        },
        onDismiss = { showFrequencyDialog = false }
    )
}

@Composable
private fun BackupActionsCard(
    state: AppSettingsState,
    onOpenBackupDialog: () -> Unit,
    onRestoreClick: () -> Unit
) {
    SettingsCard {
        SettingsSectionHeader(
            title = stringResource(Res.string.sectionBackupRestore),
            icon = Icons.Default.RestartAlt,
            iconTint = MaterialTheme.colors.primary
        )

        SettingsItemRow(
            title = stringResource(Res.string.backup_export_title),
            subtitle = if (state.isBackingUp) stringResource(Res.string.backup_in_progress) else stringResource(Res.string.backup_export_desc),
            icon = Icons.Default.CloudUpload,
            iconTint = MaterialTheme.colors.primary,
            enabled = !state.isBackingUp && !state.isRestoring,
            trailingContent = if (state.isBackingUp) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colors.primary
                    )
                }
            } else null,
            onClick = onOpenBackupDialog
        )

        Divider(color = CloudstreamTheme.extendedColors.divider)

        SettingsItemRow(
            title = stringResource(Res.string.backup_restore_title),
            subtitle = if (state.isRestoring) stringResource(Res.string.restore_in_progress) else stringResource(Res.string.backup_restore_desc),
            icon = Icons.Default.CloudDownload,
            iconTint = MaterialTheme.colors.secondary,
            enabled = !state.isBackingUp && !state.isRestoring,
            trailingContent = if (state.isRestoring) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colors.secondary
                    )
                }
            } else null,
            onClick = onRestoreClick
        )
    }
}

@Composable
private fun AutoBackupCard(
    autoBackupEnabled: Boolean,
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    autoBackupFrequency: String,
    onOpenFrequencyDialog: () -> Unit
) {
    SettingsCard {
        SettingsSectionHeader(
            title = stringResource(Res.string.backup_auto_title),
            icon = Icons.Default.Schedule,
            iconTint = MaterialTheme.colors.primary
        )

        SettingsSwitchItem(
            title = stringResource(Res.string.backup_auto_title),
            subtitle = stringResource(Res.string.backup_auto_desc),
            icon = Icons.Default.Schedule,
            checked = autoBackupEnabled,
            onCheckedChange = onAutoBackupEnabledChange
        )

        if (autoBackupEnabled) {
            Divider(color = CloudstreamTheme.extendedColors.divider)

            SettingsItemRow(
                title = stringResource(Res.string.backup_frequency),
                subtitle = stringResource(Res.string.backup_frequency_desc),
                valueText = autoBackupFrequency,
                icon = Icons.Default.Tune,
                onClick = onOpenFrequencyDialog
            )
        }
    }
}

@Composable
private fun BackupCategorySelectionDialog(
    show: Boolean,
    isBackingUp: Boolean,
    selectedCategories: Set<BackupCategory>,
    onToggleCategory: (BackupCategory) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    ActionDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.backup_select_categories_title),
        subtitle = stringResource(Res.string.backup_export_desc),
        iconVector = Icons.Default.CloudUpload,
        iconTint = MaterialTheme.colors.primary,
        confirmText = stringResource(Res.string.backup_export_button),
        confirmEnabled = selectedCategories.isNotEmpty() && !isBackingUp,
        confirmLoading = isBackingUp,
        onConfirm = onConfirm,
        cancelTextRes = Res.string.cancel,
        onCancel = onDismiss,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackupCategory.entries.forEach { category ->
                    val isSelected = selectedCategories.contains(category)
                    SelectableOptionCard(
                        isSelected = isSelected,
                        onClick = { onToggleCategory(category) }
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.15f)
                                        else CloudstreamTheme.extendedColors.divider
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = backupCategoryIcon(category),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colors.primary else CloudstreamTheme.extendedColors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(category.nameRes),
                                    style = MaterialTheme.typography.body2.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    ),
                                    color = if (isSelected) CloudstreamTheme.extendedColors.textPrimary else CloudstreamTheme.extendedColors.textSecondary
                                )
                                Text(
                                    text = stringResource(category.descriptionRes),
                                    style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                                    color = CloudstreamTheme.extendedColors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleCategory(category) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colors.primary,
                                uncheckedColor = CloudstreamTheme.extendedColors.textMuted,
                                checkmarkColor = MaterialTheme.colors.onPrimary
                            )
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BackupStatusDialogs(
    state: AppSettingsState,
    onDismiss: () -> Unit
) {
    val successRes = state.backupSuccessRes
    if (successRes != null) {
        ActionDialog(
            onDismissRequest = onDismiss,
            titleRes = Res.string.pref_category_backup,
            iconVector = Icons.Default.CheckCircle,
            iconTint = CloudStreamColors.Success,
            messageRes = successRes,
            confirmTextRes = Res.string.ok,
            onConfirm = onDismiss,
            cancelText = null,
            cancelTextRes = null,
            onCancel = null
        )
    }

    val errorRes = state.backupErrorRes
    if (errorRes != null) {
        ActionDialog(
            onDismissRequest = onDismiss,
            titleRes = Res.string.error,
            iconVector = Icons.Default.ErrorOutline,
            iconTint = CloudStreamColors.Error,
            messageRes = errorRes,
            confirmTextRes = Res.string.ok,
            onConfirm = onDismiss,
            cancelText = null,
            cancelTextRes = null,
            onCancel = null
        )
    }
}

@Composable
private fun BackupFrequencyDialog(
    show: Boolean,
    selectedFrequency: String,
    onSelectFrequency: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    val frequencies = listOf(
        stringResource(Res.string.backup_frequency_daily),
        stringResource(Res.string.backup_frequency_weekly),
        stringResource(Res.string.backup_frequency_monthly)
    )
    SettingsChoiceDialog(
        title = stringResource(Res.string.backup_frequency),
        items = frequencies,
        selectedItem = selectedFrequency,
        itemLabel = { it },
        onItemSelected = onSelectFrequency,
        onDismissRequest = onDismiss
    )
}

@Composable
fun NetworkDnsSettingsSection(
    state: AppSettingsState,
    onDohProviderSelected: (DohProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.dohProvider),
                description = stringResource(Res.string.dohProviderDesc),
                icon = Icons.Default.Dns
            )

            Spacer(modifier = Modifier.height(8.dp))

            DohSelector(
                selectedProvider = state.dohProvider,
                onProviderSelected = onDohProviderSelected
            )
        }
    }
}

@Composable
fun GeneralSettingsSection(
    state: AppSettingsState,
    onSyncWatchProgressChanged: (Boolean) -> Unit,
    onSyncScoresChanged: (Boolean) -> Unit,
    onSyncWifiOnlyChanged: (Boolean) -> Unit,
    onSkipStartupAccountSelectChanged: (Boolean) -> Unit,
    onRequestReset: () -> Unit,
    onNavigateToAccountSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.switchAccount),
                icon = Icons.Default.Person
            )

            SettingsItemRow(
                title = stringResource(Res.string.manageProfiles),
                subtitle = stringResource(Res.string.manage_profiles_desc),
                icon = Icons.Default.Person,
                onClick = { onNavigateToAccountSelect?.invoke() }
            )
        }

        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.settings_about_app),
                icon = Icons.Default.Info
            )

            SettingsItemRow(
                title = stringResource(Res.string.settings_version_title),
                valueText = stringResource(Res.string.settings_version_value),
                icon = Icons.Default.Info
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            SettingsItemRow(
                title = stringResource(Res.string.settings_architecture_title),
                valueText = stringResource(Res.string.settings_architecture_value),
                icon = Icons.Default.Tune
            )
        }

        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.resetAllSettings),
                icon = Icons.Default.RestartAlt,
                iconTint = MaterialTheme.colors.error
            )

            SettingsItemRow(
                title = stringResource(Res.string.resetAllSettings),
                subtitle = stringResource(Res.string.resetAllSettingsDesc),
                icon = Icons.Default.RestartAlt,
                iconTint = MaterialTheme.colors.error,
                onClick = onRequestReset
            )
        }
    }
}

@Composable
fun PluginsFallbackNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = CloudstreamTheme.extendedColors.textMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.settings_plugins_fallback_title),
                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                color = CloudstreamTheme.extendedColors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            BodyMutedText(
                text = stringResource(Res.string.settings_plugins_fallback_desc)
            )
        }
    }
}

@Composable
fun ErrorMessageBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colors.error.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colors.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.body2.copy(
                        color = MaterialTheme.colors.error,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = MaterialTheme.colors.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme {
        ResponsiveSettingsScaffold(
            categories = persistentListOf(
                SettingsCategory(
                    id = "appearance",
                    title = "Appearance",
                    description = "Theme & visual styling",
                    icon = Icons.Default.Palette
                ),
                SettingsCategory(
                    id = "player_subtitles",
                    title = "Player & Subtitles",
                    description = "Subtitle styles and player controls",
                    icon = Icons.Default.Subtitles
                )
            ),
            selectedCategoryId = "appearance",
            onSelectCategory = {}
        ) { category ->
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = category.title, style = MaterialTheme.typography.h6)
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenAmoledLightPreview() {
    CloudStreamTheme(theme = AppTheme.AMOLED, isDarkMode = false) {
        ResponsiveSettingsScaffold(
            categories = persistentListOf(
                SettingsCategory(
                    id = "appearance",
                    title = "Appearance",
                    description = "Theme & visual styling",
                    icon = Icons.Default.Palette
                )
            ),
            selectedCategoryId = "appearance",
            onSelectCategory = {}
        ) { category ->
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = category.title, style = MaterialTheme.typography.h6)
            }
        }
    }
}

