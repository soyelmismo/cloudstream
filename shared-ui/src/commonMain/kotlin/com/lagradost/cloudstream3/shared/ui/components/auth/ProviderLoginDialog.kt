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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import com.lagradost.cloudstream3.shared.syncproviders.AuthAPI
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Standardized Provider Login Dialog for Compose Multiplatform.
 *
 * Supports InApp credential login (username, password, email, server) based on
 * the provider's [AuthLoginRequirement] specifications.
 */
@Composable
fun ProviderLoginDialog(
    api: AuthAPI,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLogin: (AuthLoginResponse) -> Unit,
    onDismiss: () -> Unit,
    onCreateAccount: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ProviderLoginDialog(
        providerName = api.name,
        providerIcon = api.icon,
        requirements = api.inAppLoginRequirement ?: AuthLoginRequirement(username = true, password = true),
        createAccountUrl = api.createAccountUrl,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onLogin = onLogin,
        onDismiss = onDismiss,
        onCreateAccount = onCreateAccount,
        modifier = modifier
    )
}

/**
 * General standalone overload of [ProviderLoginDialog] taking decoupled parameters.
 */
@Composable
fun ProviderLoginDialog(
    providerName: String,
    providerIcon: DrawableResource? = null,
    requirements: AuthLoginRequirement = AuthLoginRequirement(username = true, password = true),
    createAccountUrl: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLogin: (AuthLoginResponse) -> Unit,
    onDismiss: () -> Unit,
    onCreateAccount: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }

    val isServerValid = !requirements.server || server.isNotBlank()
    val isUsernameValid = !requirements.username || username.isNotBlank()
    val isEmailValid = !requirements.email || email.isNotBlank()
    val isPasswordValid = !requirements.password || password.isNotBlank()
    val isFormValid = isServerValid && isUsernameValid && isEmailValid && isPasswordValid

    fun submitLogin() {
        if (isFormValid && !isLoading) {
            onLogin(
                AuthLoginResponse(
                    username = if (requirements.username) username.trim() else null,
                    password = if (requirements.password) password else null,
                    email = if (requirements.email) email.trim() else null,
                    server = if (requirements.server) server.trim() else null
                )
            )
        }
    }

    CloudStreamDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = modifier,
        maxWidth = 460.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =============================================================
            // 1. HEADER SECTION
            // =============================================================
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
                        if (providerIcon != null) {
                            Icon(
                                painter = painterResource(providerIcon),
                                contentDescription = providerName,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = providerName,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = providerName,
                            style = MaterialTheme.typography.h6.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextPrimary,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(Res.string.login),
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

            // =============================================================
            // 2. ERROR BANNER (if present)
            // =============================================================
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

            // =============================================================
            // 3. CREDENTIAL INPUT FIELDS
            // =============================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Server URL input (if required)
                if (requirements.server) {
                    CloudStreamTextField(
                        value = server,
                        onValueChange = { server = it },
                        labelRes = Res.string.auth_server,
                        placeholderRes = Res.string.auth_server_hint,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_dns_24),
                                contentDescription = null,
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = if (requirements.username || requirements.email || requirements.password) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() }
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }

                // Email input (if required)
                if (requirements.email) {
                    CloudStreamTextField(
                        value = email,
                        onValueChange = { email = it },
                        labelRes = Res.string.auth_email,
                        placeholderRes = Res.string.auth_email_hint,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (requirements.username || requirements.password) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() }
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }

                // Username input (if required)
                if (requirements.username) {
                    CloudStreamTextField(
                        value = username,
                        onValueChange = { username = it },
                        labelRes = Res.string.auth_username,
                        placeholderRes = Res.string.auth_username_hint,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = if (requirements.password) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() }
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }

                // Password input (if required)
                if (requirements.password) {
                    CloudStreamTextField(
                        value = password,
                        onValueChange = { password = it },
                        labelRes = Res.string.auth_password,
                        placeholderRes = Res.string.auth_password_hint,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() }
                        ),
                        enabled = !isLoading,
                        singleLine = true
                    )
                }
            }

            // =============================================================
            // 4. CREATE ACCOUNT LINK (if available)
            // =============================================================
            if (!createAccountUrl.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    GhostButton(
                        textRes = Res.string.create_account,
                        onClick = {
                            if (onCreateAccount != null) {
                                onCreateAccount(createAccountUrl)
                            } else {
                                uriHandler.openUri(createAccountUrl)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_open_in_new_24),
                                contentDescription = null,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        contentColor = CloudStreamColors.Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // =============================================================
            // 5. ACTION BUTTONS (Cancel & Login)
            // =============================================================
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
                    textRes = Res.string.login,
                    onClick = { submitLogin() },
                    loading = isLoading,
                    enabled = isFormValid && !isLoading
                )
            }
        }
    }
}
