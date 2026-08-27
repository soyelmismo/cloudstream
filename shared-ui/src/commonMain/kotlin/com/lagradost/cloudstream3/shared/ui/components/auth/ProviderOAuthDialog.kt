package com.lagradost.cloudstream3.shared.ui.components.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Standardized OAuth Authorization Dialog for Compose Multiplatform.
 *
 * Displays authorization instructions, a button to re-open the browser,
 * and a text field to paste the final redirect URL or access token.
 */
@Composable
fun ProviderOAuthDialog(
    repo: AuthRepo,
    authUrl: String,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onCompleteLogin: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var pastedUrlOrToken by remember { mutableStateOf("") }

    val isInputValid = pastedUrlOrToken.isNotBlank()

    fun submit() {
        if (isInputValid && !isLoading) {
            onCompleteLogin(pastedUrlOrToken.trim())
        }
    }

    CloudStreamDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = modifier,
        maxWidth = 480.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CloudStreamColors.Primary.copy(alpha = 0.15f))
                            .border(1.dp, CloudStreamColors.Primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (repo.icon != null) {
                            Icon(
                                painter = painterResource(repo.icon!!),
                                contentDescription = repo.name,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = repo.name,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.h6.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextPrimary,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(Res.string.auth_locally),
                            style = MaterialTheme.typography.caption.copy(
                                color = CloudStreamColors.TextSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
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

            // Instructions text
            Text(
                text = stringResource(Res.string.auth_oauth_instructions),
                style = MaterialTheme.typography.body2.copy(
                    color = CloudStreamColors.TextSecondary,
                    lineHeight = 20.sp
                )
            )

            // Re-open browser button
            GhostButton(
                textRes = Res.string.auth_reopen_browser,
                onClick = {
                    try {
                        uriHandler.openUri(authUrl)
                    } catch (_: Throwable) {
                        repo.openOAuth2Page()
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        tint = CloudStreamColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                contentColor = CloudStreamColors.Primary
            )

            // Error banner
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CloudStreamColors.Error.copy(alpha = 0.12f))
                            .border(1.dp, CloudStreamColors.Error.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CloudStreamColors.Error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.caption.copy(
                                color = CloudStreamColors.Error,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Input field
            CloudStreamTextField(
                value = pastedUrlOrToken,
                onValueChange = { pastedUrlOrToken = it },
                labelRes = Res.string.auth_paste_url_or_token,
                placeholderRes = Res.string.auth_paste_url_or_token,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = CloudStreamColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                ),
                enabled = !isLoading,
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostButton(
                    textRes = Res.string.cancel,
                    onClick = onDismiss,
                    enabled = !isLoading,
                    contentColor = CloudStreamColors.TextSecondary
                )

                Spacer(modifier = Modifier.width(8.dp))

                PrimaryButton(
                    textRes = Res.string.auth_complete_login,
                    onClick = { submit() },
                    loading = isLoading,
                    enabled = isInputValid && !isLoading
                )
            }
        }
    }
}
