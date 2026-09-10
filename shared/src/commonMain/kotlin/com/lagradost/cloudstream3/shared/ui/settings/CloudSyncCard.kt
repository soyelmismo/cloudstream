package com.lagradost.cloudstream3.shared.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.shared.sync.manager.CloudSyncProvider
import com.lagradost.cloudstream3.shared.sync.manager.CloudSyncState
import com.lagradost.cloudstream3.shared.ui.components.SettingsCard
import com.lagradost.cloudstream3.shared.ui.components.SettingsItemRow
import com.lagradost.cloudstream3.shared.ui.components.SettingsSectionHeader
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream4.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncCard(
    state: CloudSyncState,
    isTestingConnection: Boolean = false,
    testConnectionResult: Boolean? = null,
    pendingOAuthUrl: String? = null,
    onProviderSelected: (CloudSyncProvider) -> Unit,
    onStartGoogleAuth: () -> Unit,
    onCompleteGoogleAuth: (String) -> Unit,
    onCancelGoogleAuth: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveWebDavConfig: (url: String, username: String, pass: String) -> Unit,
    onSaveLocalPath: (path: String) -> Unit,
    onSyncNow: () -> Unit,
    onTestConnection: () -> Unit,
    onAutoSyncToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(modifier = modifier) {
        SettingsSectionHeader(
            title = stringResource(Res.string.section_cloud_sync),
            icon = Icons.Default.CloudSync,
            iconTint = MaterialTheme.colors.primary
        )

        Spacer(modifier = Modifier.padding(top = 8.dp))

        ProviderSelectionRow(
            currentProvider = state.provider,
            onProviderSelected = onProviderSelected
        )

        ProviderContentSection(
            state = state,
            onStartGoogleAuth = onStartGoogleAuth,
            onDisconnect = onDisconnect,
            onSaveWebDavConfig = onSaveWebDavConfig,
            onSaveLocalPath = onSaveLocalPath
        )

        if (state.provider != CloudSyncProvider.NONE) {
            SyncControlsSection(
                state = state,
                isTestingConnection = isTestingConnection,
                testConnectionResult = testConnectionResult,
                onSyncNow = onSyncNow,
                onTestConnection = onTestConnection,
                onAutoSyncToggled = onAutoSyncToggled
            )
        }
    }

    if (pendingOAuthUrl != null) {
        GoogleOAuthDialog(
            authUrl = pendingOAuthUrl,
            onCompleteAuth = onCompleteGoogleAuth,
            onDismiss = onCancelGoogleAuth
        )
    }
}

@Composable
fun ProviderSelectionRow(
    currentProvider: CloudSyncProvider,
    onProviderSelected: (CloudSyncProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CloudSyncProvider.entries.forEach { provider ->
            ProviderChipItem(
                provider = provider,
                isSelected = currentProvider == provider,
                onClick = { onProviderSelected(provider) }
            )
        }
    }
}

@Composable
private fun ProviderChipItem(
    provider: CloudSyncProvider,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CloudStreamFilterChip(
        label = providerDisplayName(provider),
        isSelected = isSelected,
        onClick = onClick,
        leadingIcon = providerIcon(provider),
        modifier = modifier
    )
}

@Composable
private fun providerDisplayName(provider: CloudSyncProvider): String = when (provider) {
    CloudSyncProvider.NONE -> stringResource(Res.string.cloud_sync_provider_none)
    CloudSyncProvider.GOOGLE_DRIVE -> stringResource(Res.string.cloud_sync_provider_gdrive)
    CloudSyncProvider.WEBDAV -> stringResource(Res.string.cloud_sync_provider_webdav)
    CloudSyncProvider.LOCAL_FOLDER -> stringResource(Res.string.cloud_sync_provider_local)
}

private fun providerIcon(provider: CloudSyncProvider): ImageVector = when (provider) {
    CloudSyncProvider.NONE -> Icons.Default.CloudOff
    CloudSyncProvider.GOOGLE_DRIVE -> Icons.Default.Cloud
    CloudSyncProvider.WEBDAV -> Icons.Default.Dns
    CloudSyncProvider.LOCAL_FOLDER -> Icons.Default.Folder
}

@Composable
private fun ProviderContentSection(
    state: CloudSyncState,
    onStartGoogleAuth: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveWebDavConfig: (String, String, String) -> Unit,
    onSaveLocalPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.provider) {
        CloudSyncProvider.NONE -> Unit
        CloudSyncProvider.GOOGLE_DRIVE -> GoogleDrivePanel(
            state = state,
            onStartAuth = onStartGoogleAuth,
            onDisconnect = onDisconnect,
            modifier = modifier
        )
        CloudSyncProvider.WEBDAV -> WebDavConfigPanel(
            state = state,
            onSaveConfig = onSaveWebDavConfig,
            modifier = modifier
        )
        CloudSyncProvider.LOCAL_FOLDER -> LocalFolderConfigPanel(
            state = state,
            onSavePath = onSaveLocalPath,
            modifier = modifier
        )
    }
}

@Composable
private fun GoogleDrivePanel(
    state: CloudSyncState,
    onStartAuth: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isConnected) {
        GoogleDriveConnectedView(
            state = state,
            onDisconnect = onDisconnect,
            modifier = modifier
        )
    } else {
        GoogleDriveDisconnectedView(
            onStartAuth = onStartAuth,
            modifier = modifier
        )
    }
}

@Composable
private fun GoogleDriveConnectedView(
    state: CloudSyncState,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(36.dp)
            )
            Column {
                val displayName = state.accountName ?: state.accountEmail.orEmpty()
                Text(
                    text = displayName.ifBlank { stringResource(Res.string.cloud_sync_provider_gdrive) },
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.SemiBold),
                    color = CloudStreamColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!state.accountEmail.isNullOrBlank() && state.accountName != null) {
                    Text(
                        text = state.accountEmail,
                        style = MaterialTheme.typography.caption,
                        color = CloudStreamColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        SecondaryButton(
            text = stringResource(Res.string.cloud_sync_disconnect),
            onClick = onDisconnect,
            icon = Icons.Default.Close
        )
    }
}

@Composable
private fun GoogleDriveDisconnectedView(
    onStartAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.cloud_sync_desc),
            style = MaterialTheme.typography.body2,
            color = CloudStreamColors.TextSecondary
        )
        PrimaryButton(
            text = stringResource(Res.string.cloud_sync_connect_google),
            onClick = onStartAuth,
            icon = Icons.Default.Cloud
        )
    }
}

@Composable
private fun WebDavConfigPanel(
    state: CloudSyncState,
    onSaveConfig: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(state.webDavUrl) { mutableStateOf(state.webDavUrl) }
    var username by remember(state.webDavUsername) { mutableStateOf(state.webDavUsername) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CloudStreamTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(Res.string.cloud_sync_webdav_url),
            placeholder = "https://nextcloud.example.com/remote.php/webdav",
            singleLine = true
        )
        CloudStreamTextField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(Res.string.cloud_sync_webdav_user),
            singleLine = true
        )
        CloudStreamTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.cloud_sync_webdav_pass),
            isPassword = true,
            singleLine = true
        )
        PrimaryButton(
            text = stringResource(Res.string.cloud_sync_save_config),
            onClick = { onSaveConfig(url.trim(), username.trim(), password) },
            enabled = url.isNotBlank() && username.isNotBlank(),
            icon = Icons.Default.Check
        )
    }
}

@Composable
private fun LocalFolderConfigPanel(
    state: CloudSyncState,
    onSavePath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var path by remember(state.localFolderPath) { mutableStateOf(state.localFolderPath) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CloudStreamTextField(
            value = path,
            onValueChange = { path = it },
            label = stringResource(Res.string.cloud_sync_local_path),
            placeholder = "/sdcard/CloudStream/sync",
            singleLine = true
        )
        PrimaryButton(
            text = stringResource(Res.string.cloud_sync_save_path),
            onClick = { onSavePath(path.trim()) },
            enabled = path.isNotBlank(),
            icon = Icons.Default.Check
        )
    }
}

@Composable
private fun SyncControlsSection(
    state: CloudSyncState,
    isTestingConnection: Boolean,
    testConnectionResult: Boolean?,
    onSyncNow: () -> Unit,
    onTestConnection: () -> Unit,
    onAutoSyncToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Divider(
            color = CloudStreamColors.Divider.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = if (state.isSyncing) stringResource(Res.string.cloud_sync_syncing) else stringResource(Res.string.cloud_sync_now),
                onClick = onSyncNow,
                loading = state.isSyncing,
                enabled = !state.isSyncing,
                icon = Icons.Default.Sync,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = stringResource(Res.string.cloud_sync_test_connection),
                onClick = onTestConnection,
                loading = isTestingConnection,
                enabled = !isTestingConnection && !state.isSyncing,
                modifier = Modifier.weight(1f)
            )
        }

        ConnectionTestStatus(testResult = testConnectionResult)

        SettingsItemRow(
            title = stringResource(Res.string.cloud_sync_auto_title),
            subtitle = stringResource(Res.string.cloud_sync_auto_desc),
            icon = Icons.Default.Schedule,
            trailingContent = {
                Switch(
                    checked = state.autoSyncEnabled,
                    onCheckedChange = onAutoSyncToggled,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                )
            }
        )

        LastSyncTimeDisplay(lastSyncTimestamp = state.lastSyncTimestamp)
    }
}

@Composable
private fun ConnectionTestStatus(
    testResult: Boolean?,
    modifier: Modifier = Modifier
) {
    if (testResult == null) return

    val isSuccess = testResult
    val textRes = if (isSuccess) Res.string.cloud_sync_connection_ok else Res.string.cloud_sync_connection_error
    val color = if (isSuccess) CloudStreamColors.Success else CloudStreamColors.Error
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error

    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = color
        )
    }
}

@Composable
private fun LastSyncTimeDisplay(
    lastSyncTimestamp: Long,
    modifier: Modifier = Modifier
) {
    val formattedDate = formatSyncTimestamp(lastSyncTimestamp)
    val text = if (lastSyncTimestamp > 0L) {
        stringResource(Res.string.cloud_sync_last_synced, formattedDate)
    } else {
        stringResource(Res.string.cloud_sync_last_synced, stringResource(Res.string.cloud_sync_never))
    }

    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = CloudStreamColors.TextMuted,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

private fun formatSyncTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    // Cross-platform date formatting across Android and JVM Desktop
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")
}

@Composable
fun GoogleOAuthDialog(
    authUrl: String,
    onCompleteAuth: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var pastedCode by remember { mutableStateOf("") }

    CloudStreamDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        maxWidth = 480.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(Res.string.cloud_sync_provider_gdrive),
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextPrimary
                        )
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = CloudStreamColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = stringResource(Res.string.cloud_sync_oauth_instructions),
                style = MaterialTheme.typography.body2,
                color = CloudStreamColors.TextSecondary
            )

            SecondaryButton(
                text = stringResource(Res.string.auth_reopen_browser),
                onClick = {
                    runCatching { uriHandler.openUri(authUrl) }
                },
                icon = Icons.Default.OpenInBrowser
            )

            CloudStreamTextField(
                value = pastedCode,
                onValueChange = { pastedCode = it },
                label = stringResource(Res.string.cloud_sync_paste_code),
                placeholder = "4/0A...",
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                PrimaryButton(
                    text = stringResource(Res.string.confirm),
                    onClick = {
                        val trimmed = pastedCode.trim()
                        if (trimmed.isNotEmpty()) {
                            onCompleteAuth(trimmed)
                        }
                    },
                    enabled = pastedCode.isNotBlank()
                )
            }
        }
    }
}

@Preview
@Composable
private fun CloudSyncCardPreview() {
    CloudStreamTheme {
        CloudSyncCard(
            state = CloudSyncState(
                provider = CloudSyncProvider.GOOGLE_DRIVE,
                isConnected = true,
                accountName = "John Doe",
                accountEmail = "john@example.com"
            ),
            onProviderSelected = {},
            onStartGoogleAuth = {},
            onCompleteGoogleAuth = {},
            onCancelGoogleAuth = {},
            onDisconnect = {},
            onSaveWebDavConfig = { _, _, _ -> },
            onSaveLocalPath = {},
            onSyncNow = {},
            onTestConnection = {},
            onAutoSyncToggled = {}
        )
    }
}
