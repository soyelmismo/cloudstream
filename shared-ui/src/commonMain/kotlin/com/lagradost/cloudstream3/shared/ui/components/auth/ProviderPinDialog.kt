package com.lagradost.cloudstream3.shared.ui.components.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.syncproviders.AuthAPI
import com.lagradost.cloudstream3.shared.syncproviders.AuthPinData
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Standardized Device PIN Dialog for Compose Multiplatform.
 *
 * Used for providers with device PIN / QR flow (e.g. Simkl, Trakt).
 * Displays large PIN code, verification URL, browser launch action,
 * and live verification indicator.
 */
@Composable
fun ProviderPinDialog(
    api: AuthAPI,
    pinData: AuthPinData,
    isVerifying: Boolean = true,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onOpenUrl: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ProviderPinDialog(
        userCode = pinData.userCode,
        verificationUrl = pinData.verificationUrl,
        expiresIn = pinData.expiresIn,
        providerName = api.name,
        providerIcon = api.icon,
        isVerifying = isVerifying,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onOpenUrl = onOpenUrl,
        modifier = modifier
    )
}

/**
 * Standalone parameter overload of [ProviderPinDialog].
 */
@Composable
fun ProviderPinDialog(
    userCode: String,
    verificationUrl: String,
    expiresIn: Int? = null,
    providerName: String? = null,
    providerIcon: DrawableResource? = null,
    isVerifying: Boolean = true,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onOpenUrl: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    val resolvedTitle = providerName ?: stringResource(Res.string.auth_pin_code)

    CloudStreamDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        maxWidth = 460.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                                contentDescription = resolvedTitle,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(Res.drawable.baseline_sync_24),
                                contentDescription = resolvedTitle,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.h6.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextPrimary,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(Res.string.auth_pin_code),
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
            // 3. LARGE PIN CODE DISPLAY
            // =============================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CloudStreamColors.SurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CloudStreamColors.Primary.copy(alpha = 0.5f)),
                elevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(userCode))
                        isCopied = true
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = userCode,
                        style = MaterialTheme.typography.h3.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = CloudStreamColors.Primary,
                            fontSize = 34.sp,
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isCopied) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = CloudStreamColors.Success,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(Res.string.auth_pin_copied),
                                style = MaterialTheme.typography.caption.copy(
                                    color = CloudStreamColors.Success,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp
                                )
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.auth_pin_copy),
                                style = MaterialTheme.typography.caption.copy(
                                    color = CloudStreamColors.TextMuted,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }

            // =============================================================
            // 4. INSTRUCTIONS & URL HIGHLIGHT
            // =============================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.auth_device_pin_desc, verificationUrl),
                    style = MaterialTheme.typography.body2.copy(
                        color = CloudStreamColors.TextSecondary,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Clickable URL pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CloudStreamColors.SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (onOpenUrl != null) {
                                onOpenUrl(verificationUrl)
                            } else {
                                uriHandler.openUri(verificationUrl)
                            }
                        }
                ) {
                    Text(
                        text = verificationUrl,
                        style = MaterialTheme.typography.caption.copy(
                            color = CloudStreamColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // =============================================================
            // 5. OPEN IN BROWSER PRIMARY ACTION BUTTON
            // =============================================================
            PrimaryButton(
                textRes = Res.string.auth_open_browser,
                onClick = {
                    if (onOpenUrl != null) {
                        onOpenUrl(verificationUrl)
                    } else {
                        uriHandler.openUri(verificationUrl)
                    }
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_baseline_open_in_new_24),
                        contentDescription = null,
                        tint = CloudStreamColors.OnMediaScrim,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // =============================================================
            // 6. LIVE VERIFICATION STATUS SPINNER / COUNTDOWN
            // =============================================================
            if (isVerifying) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = CloudStreamColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.auth_verifying),
                        style = MaterialTheme.typography.caption.copy(
                            color = CloudStreamColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // =============================================================
            // 7. FOOTER / CANCEL BUTTON
            // =============================================================
            GhostButton(
                textRes = Res.string.cancel,
                onClick = onDismiss,
                contentColor = CloudStreamColors.TextMuted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
