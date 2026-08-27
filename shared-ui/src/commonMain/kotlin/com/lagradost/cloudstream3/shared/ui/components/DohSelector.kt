package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Visual DoH (DNS over HTTPS) provider selector adhering to CloudStream Design System.
 * Displays secure provider options, security badges, network endpoints, and custom URL configuration.
 */
@Composable
fun DohSelector(
    selectedProvider: DohProvider,
    onProviderSelected: (DohProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDohDialog by remember { mutableStateOf(false) }
    var customDohUrl by remember { mutableStateOf("") }
    var customDohError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Informative Explainer Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CloudStreamColors.Primary.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = stringResource(Res.string.dohProvider),
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.dohProvider),
                        style = MaterialTheme.typography.subtitle2.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.Primary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BodyMutedText(
                        text = stringResource(Res.string.dohProviderDesc),
                        style = MaterialTheme.typography.caption.copy(fontSize = 12.sp)
                    )
                }
            }
        }

        // List of DoH Providers
        DohProvider.entries.forEach { provider ->
            DohProviderCard(
                provider = provider,
                isSelected = provider == selectedProvider,
                onSelect = { onProviderSelected(provider) }
            )
        }

        // Custom DoH Endpoint action button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SecondaryButton(
                text = stringResource(Res.string.custom_doh_button),
                icon = Icons.Default.AddLink,
                onClick = {
                    customDohError = null
                    showCustomDohDialog = true
                }
            )
        }
    }

    // Custom DoH URL Configuration Dialog
    if (showCustomDohDialog) {
        val invalidUrlMessage = stringResource(Res.string.custom_doh_invalid_url)
        ActionDialog(
            onDismissRequest = { showCustomDohDialog = false },
            titleRes = Res.string.custom_doh_url_title,
            iconVector = Icons.Default.Dns,
            content = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BodyMutedText(
                        text = stringResource(Res.string.custom_doh_url_desc)
                    )

                    CloudStreamTextField(
                        value = customDohUrl,
                        onValueChange = {
                            customDohUrl = it
                            customDohError = null
                        },
                        placeholder = stringResource(Res.string.custom_doh_placeholder),
                        label = stringResource(Res.string.custom_doh_url_title),
                        isError = customDohError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (customDohError != null) {
                        Text(
                            text = customDohError.orEmpty(),
                            style = MaterialTheme.typography.caption.copy(color = CloudStreamColors.Error)
                        )
                    }
                }
            },
            confirmButtonText = stringResource(Res.string.apply),
            onConfirm = {
                val trimmed = customDohUrl.trim()
                if (trimmed.startsWith("https://", ignoreCase = true) && trimmed.length > 8) {
                    showCustomDohDialog = false
                } else {
                    customDohError = invalidUrlMessage
                }
            },
            dismissButtonText = stringResource(Res.string.cancel),
            onDismiss = { showCustomDohDialog = false }
        )
    }
}

/**
 * Standardized Card displaying a DoH provider option with status badges.
 */
@Composable
fun DohProviderCard(
    provider: DohProvider,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeRes = getBadgeResForProvider(provider)
    val ipsString = if (provider.ips.isNotEmpty()) "IPs: ${provider.ips.joinToString(", ")}" else null
    val subtitleText = listOfNotNull(provider.url, ipsString).joinToString("\n").ifBlank { null }
    val titleText = if (provider == DohProvider.NONE) stringResource(Res.string.dohDisabled) else provider.displayName

    SelectableOptionCard(
        isSelected = isSelected,
        onClick = onSelect,
        title = titleText,
        subtitle = subtitleText,
        trailingContent = {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = CloudStreamColors.Primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = stringResource(badgeRes),
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = CloudStreamColors.Primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Modal dialog for selecting a DoH provider from any screen.
 */
@Composable
fun DohChoiceDialog(
    selectedProvider: DohProvider,
    onProviderSelected: (DohProvider) -> Unit,
    onDismiss: () -> Unit
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.dohProvider,
        iconVector = Icons.Default.Security,
        cancelTextRes = Res.string.close,
        onCancel = onDismiss,
        content = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(DohProvider.entries) { provider ->
                    DohProviderCard(
                        provider = provider,
                        isSelected = provider == selectedProvider,
                        onSelect = {
                            onProviderSelected(provider)
                            onDismiss()
                        }
                    )
                }
            }
        }
    )
}

private fun getBadgeResForProvider(provider: DohProvider): StringResource {
    return when (provider) {
        DohProvider.CLOUDFLARE -> Res.string.doh_badge_fast_privacy
        DohProvider.GOOGLE -> Res.string.doh_badge_reliable
        DohProvider.ADGUARD -> Res.string.doh_badge_adblocking
        DohProvider.QUAD9 -> Res.string.doh_badge_malware
        DohProvider.CANADIAN_SHIELD -> Res.string.doh_badge_privacy_shield
        DohProvider.DNS_SB -> Res.string.doh_badge_no_logs
        DohProvider.DNS_WATCH -> Res.string.doh_badge_neutrality
        DohProvider.NONE -> Res.string.doh_badge_default
    }
}
