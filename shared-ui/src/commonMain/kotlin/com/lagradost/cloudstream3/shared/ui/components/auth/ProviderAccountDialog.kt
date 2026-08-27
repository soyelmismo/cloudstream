package com.lagradost.cloudstream3.shared.ui.components.auth

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.syncproviders.AuthAPI
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.DangerButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.OutlinedActionButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Standardized Provider Account Management Dialog for Compose Multiplatform.
 *
 * Displayed when a user is already connected to an account (e.g. AniList, MAL, Simkl, OpenSubtitles).
 * Shows profile avatar/name, account switching for multiple saved accounts,
 * "Add another account" flow, and a danger-styled logout button.
 */
@Composable
fun ProviderAccountDialog(
    api: AuthAPI,
    currentUser: AuthUser?,
    accounts: List<AuthData> = emptyList(),
    onSelectAccount: (AuthData) -> Unit,
    onAddAccount: () -> Unit,
    onLogout: (AuthUser) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProviderAccountDialog(
        providerName = api.name,
        providerIcon = api.icon,
        currentUser = currentUser,
        accounts = accounts,
        onSelectAccount = onSelectAccount,
        onAddAccount = onAddAccount,
        onLogout = onLogout,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * Convenience overload of [ProviderAccountDialog] taking an [AuthRepo].
 */
@Composable
fun ProviderAccountDialog(
    repo: AuthRepo,
    onSelectAccount: (AuthData) -> Unit,
    onAddAccount: () -> Unit,
    onLogout: (AuthUser) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProviderAccountDialog(
        providerName = repo.name,
        providerIcon = repo.icon,
        currentUser = repo.authUser(),
        accounts = repo.accounts.toList(),
        onSelectAccount = onSelectAccount,
        onAddAccount = onAddAccount,
        onLogout = onLogout,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * Decoupled standalone parameter overload of [ProviderAccountDialog].
 */
@Composable
fun ProviderAccountDialog(
    providerName: String,
    providerIcon: DrawableResource? = null,
    currentUser: AuthUser?,
    accounts: List<AuthData> = emptyList(),
    onSelectAccount: (AuthData) -> Unit,
    onAddAccount: () -> Unit,
    onLogout: (AuthUser) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    CloudStreamDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        maxWidth = 480.dp
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
                                painter = painterResource(Res.drawable.baseline_sync_24),
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
                            text = stringResource(Res.string.auth_current_account),
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
            // 2. ACTIVE ACCOUNT HERO CARD
            // =============================================================
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CloudStreamColors.SurfaceVariant,
                border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.35f)),
                elevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    // Profile Avatar
                    AccountAvatar(
                        user = currentUser,
                        size = 52.dp
                    )

                    // Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentUser?.name ?: stringResource(Res.string.account),
                            style = MaterialTheme.typography.subtitle1.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextPrimary,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Active status badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CloudStreamColors.Success.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CloudStreamColors.Success.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(CloudStreamColors.Success)
                                    )
                                    Text(
                                        text = stringResource(Res.string.auth_active_badge),
                                        style = MaterialTheme.typography.caption.copy(
                                            color = CloudStreamColors.Success,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.5.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = providerName,
                                style = MaterialTheme.typography.caption.copy(
                                    color = CloudStreamColors.TextSecondary,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // =============================================================
            // 3. SAVED ACCOUNTS LIST (Switch Accounts)
            // =============================================================
            val otherAccounts = accounts.filter { it.user.id != currentUser?.id }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.auth_saved_accounts),
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )

                if (otherAccounts.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((otherAccounts.size * 60).coerceAtMost(180).dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(otherAccounts, key = { it.user.id }) { account ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CloudStreamColors.SurfaceElevated,
                                border = BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectAccount(account) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    AccountAvatar(
                                        user = account.user,
                                        size = 36.dp
                                    )

                                    Text(
                                        text = account.user.name ?: stringResource(Res.string.account),
                                        style = MaterialTheme.typography.body2.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = CloudStreamColors.TextPrimary,
                                            fontSize = 14.sp
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = stringResource(Res.string.switch_account),
                                        style = MaterialTheme.typography.caption.copy(
                                            color = CloudStreamColors.Primary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.auth_no_accounts),
                        style = MaterialTheme.typography.caption.copy(
                            color = CloudStreamColors.TextMuted,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // =============================================================
            // 4. ADD ANOTHER ACCOUNT ACTION
            // =============================================================
            OutlinedActionButton(
                textRes = Res.string.add_account,
                onClick = onAddAccount,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = CloudStreamColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // =============================================================
            // 5. ACTION BUTTONS (Logout & Close)
            // =============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logout Button
                DangerButton(
                    textRes = Res.string.logout,
                    onClick = {
                        currentUser?.let { onLogout(it) }
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_baseline_exit_24),
                            contentDescription = null,
                            tint = CloudStreamColors.OnMediaScrim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                // Close Button
                GhostButton(
                    textRes = Res.string.close,
                    onClick = onDismiss,
                    contentColor = CloudStreamColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Visual Avatar representation for [AuthUser].
 * Renders remote profile picture if available, with a themed initials circle fallback.
 */
@Composable
private fun AccountAvatar(
    user: AuthUser?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(CloudStreamColors.Primary.copy(alpha = 0.15f))
            .border(1.dp, CloudStreamColors.Primary.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val pictureUrl = user?.profilePicture
        if (!pictureUrl.isNullOrBlank()) {
            AsyncImage(
                url = pictureUrl,
                contentDescription = user.name,
                headers = user.profilePictureHeaders,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(CloudStreamColors.SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user.name?.firstOrNull()?.uppercase() ?: "?"),
                            style = MaterialTheme.typography.subtitle1.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.Primary
                            )
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(CloudStreamColors.Primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user.name?.firstOrNull()?.uppercase() ?: "?"),
                            style = MaterialTheme.typography.subtitle1.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.Primary
                            )
                        )
                    }
                }
            )
        } else {
            val initial = user?.name?.firstOrNull()?.uppercase() ?: ""
            if (initial.isNotEmpty()) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.Primary,
                        fontSize = (size.value * 0.42f).sp
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}
