package com.lagradost.cloudstream3.shared.ui.settings

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
import cloudstream.shared_ui.generated.resources.*
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
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsEvent
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsState
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel

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
        listOf(
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
                onEvent = appSettingsViewModel::handleEvent,
                onOpenLanguageDialog = { showLanguageDialog = true }
            )
            "player_subtitles" -> PlayerSubtitlesSettingsSection(
                state = appState,
                onEvent = appSettingsViewModel::handleEvent
            )
            "sync_accounts" -> SyncAccountsSettingsSection(
                state = appState,
                onEvent = appSettingsViewModel::handleEvent,
                onNavigateToAccountSelect = onNavigateToAccountSelect
            )
            "backup_restore" -> BackupRestoreSettingsSection(
                state = appState,
                onEvent = appSettingsViewModel::handleEvent,
                onRequestReset = { showResetDialog = true }
            )
            "network_dns" -> NetworkDnsSettingsSection(
                state = appState,
                onEvent = appSettingsViewModel::handleEvent
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
                onEvent = appSettingsViewModel::handleEvent,
                onRequestReset = { showResetDialog = true },
                onNavigateToAccountSelect = onNavigateToAccountSelect
            )
        }
    }

    // Modal: Reset Preferences Confirmation
    if (showResetDialog) {
        ConfirmDeleteDialog(
            onConfirm = {
                appSettingsViewModel.handleEvent(AppSettingsEvent.ResetToDefaults)
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
            titleRes = Res.string.resetConfirmTitle,
            messageRes = Res.string.resetConfirmDesc,
            confirmTextRes = Res.string.resetToDefaultsButton
        )
    }

    // Modal: App Language Dialog
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
                appSettingsViewModel.handleEvent(AppSettingsEvent.SetAppLanguage(it.first))
                showLanguageDialog = false
            },
            onDismissRequest = { showLanguageDialog = false }
        )
    }
}

/**
 * 1. Appearance Section
 */
@Composable
fun AppearanceSettingsSection(
    state: AppSettingsState,
    onEvent: (AppSettingsEvent) -> Unit,
    onOpenLanguageDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Visual Theme Selector
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.theme),
                icon = Icons.Default.Palette
            )

            Spacer(modifier = Modifier.height(8.dp))

            ThemeSelector(
                selectedTheme = state.theme,
                onThemeSelected = { onEvent(AppSettingsEvent.SetTheme(it)) },
                isDarkMode = state.isDarkMode
            )
        }

        // Dark Mode & System Preferences
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.sectionAppearance),
                icon = Icons.Default.Brightness4
            )

            SettingsSwitchItem(
                title = stringResource(Res.string.darkMode),
                subtitle = stringResource(Res.string.darkModeDesc),
                checked = state.isDarkMode,
                onCheckedChange = { onEvent(AppSettingsEvent.SetDarkMode(it)) }
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

/**
 * 2. Player & Subtitles Section
 */
@Composable
fun PlayerSubtitlesSettingsSection(
    state: AppSettingsState,
    onEvent: (AppSettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWifiQualityDialog by remember { mutableStateOf(false) }
    var showMobileQualityDialog by remember { mutableStateOf(false) }
    var showSoftwareDecodingDialog by remember { mutableStateOf(false) }
    var showSubtitleEncodingDialog by remember { mutableStateOf(false) }

    val autoText = stringResource(Res.string.quality_auto)
    val fourKText = stringResource(Res.string.quality_4k)
    val hdText = stringResource(Res.string.quality_hd)
    val sdText = stringResource(Res.string.quality_sd)

    val qualityOptions = remember(autoText, fourKText, hdText, sdText) {
        listOf(
            0 to autoText,
            2160 to "2160p ($fourKText)",
            1440 to "1440p ($hdText)",
            1080 to "1080p ($hdText)",
            720 to "720p ($hdText)",
            480 to "480p ($sdText)",
            360 to "360p ($sdText)",
            240 to "240p ($sdText)"
        )
    }

    val autoDecodingText = stringResource(Res.string.automatic)
    val hwSwText = stringResource(Res.string.decoding_hw_sw)
    val hwOnlyText = stringResource(Res.string.decoding_hw_only)
    val swPreferredText = stringResource(Res.string.decoding_sw_preferred)
    val softwareDecodingOptions = remember(autoDecodingText, hwSwText, hwOnlyText, swPreferredText) {
        listOf(
            -1 to autoDecodingText,
            0 to hwSwText,
            1 to hwOnlyText,
            2 to swPreferredText
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
        // Video Quality & Playback Preferences
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.video_quality),
                icon = Icons.Default.Tune
            )

            // Preferred Video Quality (WiFi)
            SettingsItemRow(
                title = stringResource(Res.string.watch_quality_pref),
                subtitle = stringResource(Res.string.video_quality),
                valueText = qualityOptions.firstOrNull { it.first == state.qualityWifi }?.second ?: autoText,
                onClick = { showWifiQualityDialog = true }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            // Preferred Video Quality (Mobile Data)
            SettingsItemRow(
                title = stringResource(Res.string.watch_quality_pref_data),
                subtitle = stringResource(Res.string.video_quality),
                valueText = qualityOptions.firstOrNull { it.first == state.qualityMobile }?.second ?: autoText,
                onClick = { showMobileQualityDialog = true }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            // Software Decoding Option
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
                onCheckedChange = { onEvent(AppSettingsEvent.SetShowSourcesOnPlay(it)) }
            )
        }

        // Subtitle Live Customizer
        SubtitleCustomizer(
            style = state.subtitleStyle,
            onStyleChanged = { onEvent(AppSettingsEvent.SetDefaultSubtitleStyle(it)) }
        )

        // Subtitle Character Encoding Card
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

    // Modal: Preferred WiFi Quality Dialog
    if (showWifiQualityDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.watch_quality_pref),
            items = qualityOptions,
            selectedItem = qualityOptions.firstOrNull { it.first == state.qualityWifi } ?: qualityOptions.first(),
            itemLabel = { it.second },
            onItemSelected = {
                onEvent(AppSettingsEvent.SetQualityWifi(it.first))
                showWifiQualityDialog = false
            },
            onDismissRequest = { showWifiQualityDialog = false }
        )
    }

    // Modal: Preferred Mobile Quality Dialog
    if (showMobileQualityDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.watch_quality_pref_data),
            items = qualityOptions,
            selectedItem = qualityOptions.firstOrNull { it.first == state.qualityMobile } ?: qualityOptions.first(),
            itemLabel = { it.second },
            onItemSelected = {
                onEvent(AppSettingsEvent.SetQualityMobile(it.first))
                showMobileQualityDialog = false
            },
            onDismissRequest = { showMobileQualityDialog = false }
        )
    }

    // Modal: Software Decoding Mode Dialog
    if (showSoftwareDecodingDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.software_decoding),
            items = softwareDecodingOptions,
            selectedItem = softwareDecodingOptions.firstOrNull { it.first == state.softwareDecoding } ?: softwareDecodingOptions.first(),
            itemLabel = { it.second },
            onItemSelected = {
                onEvent(AppSettingsEvent.SetSoftwareDecoding(it.first))
                showSoftwareDecodingDialog = false
            },
            onDismissRequest = { showSoftwareDecodingDialog = false }
        )
    }

    // Modal: Subtitle Character Encoding Dialog
    if (showSubtitleEncodingDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.subtitles_encoding),
            items = subtitleEncodings,
            selectedItem = subtitleEncodings.firstOrNull { it.equals(state.subtitleEncoding, ignoreCase = true) } ?: subtitleEncodings.first(),
            itemLabel = { it },
            onItemSelected = {
                onEvent(AppSettingsEvent.SetSubtitleEncoding(it))
                showSubtitleEncodingDialog = false
            },
            onDismissRequest = { showSubtitleEncodingDialog = false }
        )
    }
}

/**
 * 3. Sync & Accounts Section (AniList, MAL, Simkl, Kitsu, OpenSubtitles, Subdl, Addic7ed, SubSource, and Sync Preferences)
 */
@Composable
fun SyncAccountsSettingsSection(
    state: AppSettingsState,
    onEvent: (AppSettingsEvent) -> Unit,
    onNavigateToAccountSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val syncProviders = remember { AccountManager.syncApis.filter { it.requiresLogin } }
    val subtitleProviders = remember { AccountManager.subtitleProviders.toList() }

    // Active Dialog States
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

    // Simkl PIN verification polling
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tracking & Scrobbling Card (AniList, MAL, Simkl, Kitsu)
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.sync_category_tracking),
                description = stringResource(Res.string.sync_category_tracking_desc),
                icon = Icons.Default.Person,
                iconTint = MaterialTheme.colors.primary
            )

            syncProviders.forEachIndexed { index, repo ->
                val authUser = state.activeAuthAccounts[repo.idPrefix]?.user
                SettingsItemRow(
                    title = repo.name,
                    subtitle = if (authUser != null) {
                        stringResource(Res.string.sync_logged_in_as, authUser.name ?: "")
                    } else {
                        stringResource(Res.string.sync_not_connected)
                    },
                    onClick = {
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
                                onEvent(AppSettingsEvent.StartOAuthLogin(repo))
                            }
                        } else if (repo.hasInApp) {
                            loginErrorMessage = null
                            isLoginLoading = false
                            activeLoginDialogRepo = repo
                        }
                    }
                )

                if (index < syncProviders.lastIndex) {
                    Divider(color = CloudstreamTheme.extendedColors.divider)
                }
            }
        }

        // 2. Subtitle Accounts Card (OpenSubtitles, Subdl, Addic7ed, SubSource)
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.sync_category_subtitles),
                description = stringResource(Res.string.sync_category_subtitles_desc),
                icon = Icons.Default.Subtitles,
                iconTint = MaterialTheme.colors.secondary
            )

            subtitleProviders.forEachIndexed { index, repo ->
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
                            if (authUser != null) {
                                activeAccountDialogRepo = repo
                            } else if (repo.hasInApp) {
                                loginErrorMessage = null
                                isLoginLoading = false
                                activeLoginDialogRepo = repo
                            } else if (repo.hasOAuth2) {
                                onEvent(AppSettingsEvent.StartOAuthLogin(repo))
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
                            }
                        } else {
                            activeInfoDialogRepo = repo
                        }
                    }
                )

                if (index < subtitleProviders.lastIndex) {
                    Divider(color = CloudstreamTheme.extendedColors.divider)
                }
            }
        }

        // 3. Sync Preferences Card
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.sync_category_preferences),
                icon = Icons.Default.Tune,
                iconTint = MaterialTheme.colors.primary
            )

            // Watch Progress
            SettingsSwitchItem(
                title = stringResource(Res.string.sync_watch_progress),
                subtitle = stringResource(Res.string.sync_watch_progress_desc),
                checked = state.syncWatchProgress,
                onCheckedChange = { onEvent(AppSettingsEvent.SetSyncWatchProgress(it)) }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            // Scores & Ratings
            SettingsSwitchItem(
                title = stringResource(Res.string.sync_scores),
                subtitle = stringResource(Res.string.sync_scores_desc),
                checked = state.syncScores,
                onCheckedChange = { onEvent(AppSettingsEvent.SetSyncScores(it)) }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            // Wi-Fi Only
            SettingsSwitchItem(
                title = stringResource(Res.string.sync_wifi_only),
                subtitle = stringResource(Res.string.sync_wifi_only_desc),
                checked = state.syncWifiOnly,
                onCheckedChange = { onEvent(AppSettingsEvent.SetSyncWifiOnly(it)) }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            // Skip profile select on startup
            SettingsSwitchItem(
                title = stringResource(Res.string.skip_startup_account_select_pref),
                subtitle = stringResource(Res.string.skip_startup_account_select_desc),
                checked = state.skipStartupAccountSelect,
                onCheckedChange = { onEvent(AppSettingsEvent.SetSkipStartupAccountSelect(it)) }
            )
        }
    }

    // Modal Dialog: Connected Account Management (Switch, Add, Logout, Open in Browser)
    val currentAccountRepo = activeAccountDialogRepo
    if (currentAccountRepo != null) {
        val currentAuth = state.activeAuthAccounts[currentAccountRepo.idPrefix]
        ProviderAccountDialog(
            providerName = currentAccountRepo.name,
            providerIcon = currentAccountRepo.icon,
            currentUser = currentAuth?.user,
            accounts = currentAccountRepo.accounts.toList(),
            onSelectAccount = { data ->
                onEvent(AppSettingsEvent.SwitchActiveAccount(currentAccountRepo, data.user.id))
            },
            onAddAccount = {
                val targetRepo = currentAccountRepo
                activeAccountDialogRepo = null
                if (targetRepo.hasInApp) {
                    loginErrorMessage = null
                    isLoginLoading = false
                    activeLoginDialogRepo = targetRepo
                } else if (targetRepo.hasPin) {
                    pinErrorMessage = null
                    isPinLoading = true
                    activePinDialogRepo = targetRepo
                    coroutineScope.launch {
                        try {
                            activePinData = targetRepo.pinRequest()
                        } catch (t: Throwable) {
                            pinErrorMessage = t.message
                        } finally {
                            isPinLoading = false
                        }
                    }
                } else if (targetRepo.hasOAuth2) {
                    onEvent(AppSettingsEvent.StartOAuthLogin(targetRepo))
                }
            },
            onLogout = { user ->
                onEvent(AppSettingsEvent.LogoutAccount(currentAccountRepo, user))
                activeAccountDialogRepo = null
            },
            onDismiss = {
                activeAccountDialogRepo = null
            }
        )
    }

    // Modal Dialog: In-App Credentials Login (Kitsu, OpenSubtitles, Subdl)
    val currentLoginRepo = activeLoginDialogRepo
    if (currentLoginRepo != null) {
        ProviderLoginDialog(
            api = currentLoginRepo.api,
            isLoading = isLoginLoading,
            errorMessage = loginErrorMessage,
            onLogin = { formResponse ->
                coroutineScope.launch {
                    isLoginLoading = true
                    loginErrorMessage = null
                    try {
                        val success = currentLoginRepo.login(formResponse)
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
            onDismiss = {
                activeLoginDialogRepo = null
                loginErrorMessage = null
                isLoginLoading = false
            },
            onCreateAccount = { url ->
                AuthRepo.openBrowserHandler?.invoke(url)
            }
        )
    }

    // Modal Dialog: Device PIN Flow (Simkl)
    val currentPinRepo = activePinDialogRepo
    if (currentPinRepo != null) {
        val pinData = activePinData
        if (pinData != null) {
            ProviderPinDialog(
                api = currentPinRepo.api,
                pinData = pinData,
                isVerifying = true,
                errorMessage = pinErrorMessage,
                onDismiss = {
                    activePinDialogRepo = null
                    activePinData = null
                    pinErrorMessage = null
                },
                onOpenUrl = { url ->
                    AuthRepo.openBrowserHandler?.invoke(url)
                }
            )
        } else if (isPinLoading || pinErrorMessage != null) {
            ActionDialog(
                onDismissRequest = {
                    activePinDialogRepo = null
                    isPinLoading = false
                    pinErrorMessage = null
                },
                title = currentPinRepo.name,
                iconVector = Icons.Default.Person,
                iconTint = providerBrandColor(currentPinRepo.idPrefix),
                message = pinErrorMessage ?: stringResource(Res.string.auth_waiting_for_pin),
                cancelTextRes = Res.string.cancel,
                onCancel = {
                    activePinDialogRepo = null
                    isPinLoading = false
                    pinErrorMessage = null
                }
            )
        }
    }

    // Modal Dialog: OAuth Authorization Flow with URL/Token paste
    val currentOAuthRepo = activeOAuthDialogRepo
    val currentOAuthUrl = activeOAuthUrl
    if (currentOAuthRepo != null && currentOAuthUrl != null) {
        ProviderOAuthDialog(
            repo = currentOAuthRepo,
            authUrl = currentOAuthUrl,
            isLoading = isOAuthLoading,
            errorMessage = oauthErrorMessage,
            onCompleteLogin = { redirectUrlOrToken ->
                isOAuthLoading = true
                oauthErrorMessage = null
                coroutineScope.launch {
                    try {
                        val success = currentOAuthRepo.login(redirectUrlOrToken)
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
            onDismiss = {
                activeOAuthDialogRepo = null
                activeOAuthUrl = null
                oauthErrorMessage = null
            }
        )
    }

    // Modal Dialog: Informative dialog for no-login subtitle providers (Addic7ed, SubSource)
    val currentInfoRepo = activeInfoDialogRepo
    if (currentInfoRepo != null) {
        val siteUrl = currentInfoRepo.createAccountUrl ?: currentInfoRepo.api.createAccountUrl
        ActionDialog(
            onDismissRequest = { activeInfoDialogRepo = null },
            title = currentInfoRepo.name,
            iconVector = Icons.Default.Subtitles,
            iconTint = MaterialTheme.colors.secondary,
            messageRes = Res.string.sync_no_account_needed,
            confirmTextRes = if (siteUrl != null) Res.string.open_in_browser else null,
            onConfirm = if (siteUrl != null) {
                {
                    AuthRepo.openBrowserHandler?.invoke(siteUrl)
                    activeInfoDialogRepo = null
                }
            } else null,
            cancelTextRes = Res.string.close,
            onCancel = { activeInfoDialogRepo = null }
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

/**
 * Categories available for backup and restore operations.
 */
private fun backupCategoryIcon(category: BackupCategory): ImageVector = when (category) {
    BackupCategory.SETTINGS -> Icons.Default.Settings
    BackupCategory.WATCH_PROGRESS -> Icons.Default.History
    BackupCategory.BOOKMARKS -> Icons.Default.Bookmark
    BackupCategory.PLUGINS -> Icons.Default.Extension
    BackupCategory.SYNC_ACCOUNTS -> Icons.Default.Person
}

/**
 * 4. Backup & Restore Section (Export, Import, Auto-backup)
 */
@Composable
fun BackupRestoreSettingsSection(
    state: AppSettingsState,
    onEvent: (AppSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRequestReset: (() -> Unit)? = null
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
        // 1. Tarjeta "Copia de Seguridad y Restauración"
        SettingsCard {
            SettingsSectionHeader(
                title = stringResource(Res.string.sectionBackupRestore),
                icon = Icons.Default.RestartAlt,
                iconTint = MaterialTheme.colors.primary
            )

            // Fila "Crear copia de seguridad"
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
                onClick = {
                    selectedBackupCategories = BackupCategory.entries.toSet()
                    showBackupDialog = true
                }
            )

            Divider(color = CloudstreamTheme.extendedColors.divider)

            // Fila "Restaurar datos"
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
                onClick = {
                    onEvent(AppSettingsEvent.ImportBackupWithPicker)
                }
            )
        }

        // 2. Tarjeta "Copia Automática"
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
                onCheckedChange = { autoBackupEnabled = it }
            )

            if (autoBackupEnabled) {
                Divider(color = CloudstreamTheme.extendedColors.divider)

                SettingsItemRow(
                    title = stringResource(Res.string.backup_frequency),
                    subtitle = stringResource(Res.string.backup_frequency_desc),
                    valueText = autoBackupFrequency,
                    icon = Icons.Default.Tune,
                    onClick = { showFrequencyDialog = true }
                )
            }
        }
    }

    // Modal: Backup Category Selection Dialog
    if (showBackupDialog) {
        ActionDialog(
            onDismissRequest = {
                if (!state.isBackingUp) showBackupDialog = false
            },
            title = stringResource(Res.string.backup_select_categories_title),
            subtitle = stringResource(Res.string.backup_export_desc),
            iconVector = Icons.Default.CloudUpload,
            iconTint = MaterialTheme.colors.primary,
            confirmText = stringResource(Res.string.backup_export_button),
            confirmEnabled = selectedBackupCategories.isNotEmpty() && !state.isBackingUp,
            confirmLoading = state.isBackingUp,
            onConfirm = {
                onEvent(AppSettingsEvent.ExportBackupWithPicker(selectedBackupCategories))
                showBackupDialog = false
            },
            cancelTextRes = Res.string.cancel,
            onCancel = {
                if (!state.isBackingUp) showBackupDialog = false
            },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackupCategory.entries.forEach { category ->
                        val isSelected = selectedBackupCategories.contains(category)
                        SelectableOptionCard(
                            isSelected = isSelected,
                            onClick = {
                                selectedBackupCategories = if (isSelected) {
                                    selectedBackupCategories - category
                                } else {
                                    selectedBackupCategories + category
                                }
                            }
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
                                onCheckedChange = { checked ->
                                    selectedBackupCategories = if (checked) {
                                        selectedBackupCategories + category
                                    } else {
                                        selectedBackupCategories - category
                                    }
                                },
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

    // Modal: Backup Success Dialog
    val successRes = state.backupSuccessRes
    if (successRes != null) {
        ActionDialog(
            onDismissRequest = { onEvent(AppSettingsEvent.ClearBackupMessage) },
            titleRes = Res.string.pref_category_backup,
            iconVector = Icons.Default.CheckCircle,
            iconTint = CloudStreamColors.Success,
            messageRes = successRes,
            confirmTextRes = Res.string.ok,
            onConfirm = { onEvent(AppSettingsEvent.ClearBackupMessage) },
            cancelText = null,
            cancelTextRes = null,
            onCancel = null
        )
    }

    // Modal: Backup Error Dialog
    val errorRes = state.backupErrorRes
    if (errorRes != null) {
        ActionDialog(
            onDismissRequest = { onEvent(AppSettingsEvent.ClearBackupMessage) },
            titleRes = Res.string.error,
            iconVector = Icons.Default.ErrorOutline,
            iconTint = CloudStreamColors.Error,
            messageRes = errorRes,
            confirmTextRes = Res.string.ok,
            onConfirm = { onEvent(AppSettingsEvent.ClearBackupMessage) },
            cancelText = null,
            cancelTextRes = null,
            onCancel = null
        )
    }

    // Modal: Backup Frequency Dialog
    if (showFrequencyDialog) {
        val frequencies = listOf(
            stringResource(Res.string.backup_frequency_daily),
            stringResource(Res.string.backup_frequency_weekly),
            stringResource(Res.string.backup_frequency_monthly)
        )
        SettingsChoiceDialog(
            title = stringResource(Res.string.backup_frequency),
            items = frequencies,
            selectedItem = autoBackupFrequency,
            itemLabel = { it },
            onItemSelected = {
                autoBackupFrequency = it
                showFrequencyDialog = false
            },
            onDismissRequest = { showFrequencyDialog = false }
        )
    }
}

/**
 * 5. Network & DNS Section
 */
@Composable
fun NetworkDnsSettingsSection(
    state: AppSettingsState,
    onEvent: (AppSettingsEvent) -> Unit,
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
                onProviderSelected = { onEvent(AppSettingsEvent.SetDohProvider(it)) }
            )
        }
    }
}

/**
 * 6. General & Reset Section
 */
@Composable
fun GeneralSettingsSection(
    state: AppSettingsState,
    onEvent: (AppSettingsEvent) -> Unit,
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
        // User Profiles Card
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

        // App Information Card (Version & Architecture)
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

        // Factory Reset Card
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
