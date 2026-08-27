package com.lagradost.cloudstream3.shared.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Source
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsEvent
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Convenience ViewModel overload for AddRepositoryDialog.
 */
@Composable
fun AddRepositoryDialog(
    viewModel: PluginsSettingsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AddRepositoryDialog(
        onDismiss = onDismiss,
        onAddRepository = { url, name ->
            viewModel.handleEvent(PluginsSettingsEvent.AddRepository(url, name))
        },
        modifier = modifier
    )
}

/**
 * Modal Dialog for adding a repository via URL with clipboard paste support and real-time validation.
 */
@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAddRepository: (url: String, name: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    var repoUrl by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    val normalizedUrl = remember(repoUrl) {
        PluginsSettingsViewModel.normalizeRepoUrl(repoUrl)
    }
    val isValidUrl = remember(normalizedUrl) {
        PluginsSettingsViewModel.isValidRepoUrl(normalizedUrl)
    }
    val showError = hasAttemptedSubmit && !isValidUrl

    val isAddEnabled = repoUrl.trim().isNotBlank()

    ActionDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        titleRes = Res.string.add_extension_repo_title,
        iconVector = Icons.Default.Source,
        iconTint = CloudStreamColors.Primary,
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                PrimaryButton(
                    text = stringResource(Res.string.add_repo_button),
                    icon = Icons.Default.Add,
                    enabled = isAddEnabled,
                    onClick = {
                        hasAttemptedSubmit = true
                        if (isValidUrl) {
                            onAddRepository(normalizedUrl, repoName.trim().ifBlank { null })
                            onDismiss()
                        }
                    }
                )
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BodyMutedText(
                    text = stringResource(Res.string.add_extension_repo_desc)
                )

                // 1. Repository URL Field
                CloudStreamTextField(
                    value = repoUrl,
                    onValueChange = {
                        repoUrl = it
                        hasAttemptedSubmit = false
                    },
                    label = stringResource(Res.string.repository_url_required),
                    placeholder = stringResource(Res.string.repoUrlPlaceholder),
                    isError = showError,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Paste button from Clipboard
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { clipText ->
                                        if (clipText.isNotBlank()) {
                                            repoUrl = clipText.trim()
                                            hasAttemptedSubmit = false
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = stringResource(Res.string.pasteFromClipboard),
                                    tint = MaterialTheme.colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (repoUrl.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        repoUrl = ""
                                        hasAttemptedSubmit = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(Res.string.clear),
                                        tint = CloudStreamColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (showError) {
                    Text(
                        text = stringResource(Res.string.invalidRepoUrl),
                        style = MaterialTheme.typography.caption.copy(
                            color = MaterialTheme.colors.error,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // 2. Repository Name Field (Optional)
                CloudStreamTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = stringResource(Res.string.repository_name_optional_form),
                    placeholder = stringResource(Res.string.repo_example_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    )
}
