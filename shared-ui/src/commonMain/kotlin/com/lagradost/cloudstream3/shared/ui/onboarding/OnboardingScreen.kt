package com.lagradost.cloudstream3.shared.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import com.lagradost.cloudstream3.shared.ui.components.ProvideAppLocale
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SubtitleText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.TitleText
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.OnboardingEvent
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.OnboardingState
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.OnboardingStep
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.OnboardingViewModel
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.StarterRepoOption
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider

/**
 * Onboarding setup wizard providing first-run configuration.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    if (state.hasCompleted) {
        onComplete()
    }

    ProvideAppLocale(languageCode = state.selectedLanguage) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(CloudStreamColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Step Progress Indicator
                StepProgressHeader(
                    currentStep = state.currentStep,
                    totalSteps = OnboardingStep.entries.size
                )

                // Animated Step Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = state.currentStep,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                            }
                        }
                    ) { step ->
                        when (step) {
                            OnboardingStep.WELCOME_LANGUAGE -> WelcomeLanguageStep(
                                state = state,
                                onLanguageSelected = { viewModel.onEvent(OnboardingEvent.SelectLanguage(it)) }
                            )

                            OnboardingStep.LAYOUT_THEME -> LayoutThemeStep(
                                state = state,
                                onLayoutSelected = { viewModel.onEvent(OnboardingEvent.SelectLayoutMode(it)) },
                                onThemeSelected = { viewModel.onEvent(OnboardingEvent.SelectTheme(it)) }
                            )

                            OnboardingStep.PLUGINS_REPOSITORIES -> PluginsSetupStep(
                                state = state,
                                onToggleRepo = { viewModel.onEvent(OnboardingEvent.ToggleStarterRepo(it)) }
                            )

                            OnboardingStep.DNS_SECURITY -> DnsSecurityStep(
                                state = state,
                                onDohSelected = { viewModel.onEvent(OnboardingEvent.SelectDohProvider(it)) }
                            )

                            OnboardingStep.PROFILE_SETUP -> ProfileSetupStep(
                                state = state,
                                onNameChange = { viewModel.onEvent(OnboardingEvent.SetProfileName(it)) },
                                onAvatarChange = { viewModel.onEvent(OnboardingEvent.SetProfileAvatar(it)) }
                            )
                        }
                    }
                }

                // Bottom Navigation Actions
                WizardNavigationFooter(
                    currentStep = state.currentStep,
                    isLastStep = state.currentStep == OnboardingStep.PROFILE_SETUP,
                    onPrevious = { viewModel.onEvent(OnboardingEvent.PreviousStep) },
                    onNext = { viewModel.onEvent(OnboardingEvent.NextStep) },
                    onSkip = { viewModel.onEvent(OnboardingEvent.SkipOnboarding) },
                    onFinish = { viewModel.onEvent(OnboardingEvent.CompleteOnboarding) }
                )
            }
        }
    }
}

/**
 * Visual step indicator dots at the top of the wizard.
 */
@Composable
private fun StepProgressHeader(
    currentStep: OnboardingStep,
    totalSteps: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep.ordinal
            val isCompleted = i < currentStep.ordinal
            Box(
                modifier = Modifier
                    .size(width = if (isActive) 28.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isActive -> CloudStreamColors.Primary
                            isCompleted -> CloudStreamColors.Primary.copy(alpha = 0.5f)
                            else -> CloudStreamColors.SurfaceElevated
                        }
                    )
            )
        }
    }
}

/**
 * Step 1: Welcome message and App Language selector.
 */
@Composable
private fun WelcomeLanguageStep(
    state: OnboardingState,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // App Monogram / Logo
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(CloudStreamColors.Primary, CloudStreamColors.Primary.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                contentDescription = null,
                tint = MaterialTheme.colors.onPrimary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        TitleText(
            textRes = Res.string.onboardingWelcomeTitle,
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        BodyMutedText(
            textRes = Res.string.onboardingWelcomeDesc,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        SubtitleText(
            textRes = Res.string.appLanguage,
            fontWeight = FontWeight.SemiBold,
            color = CloudStreamColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Language Choice Chips with D-Pad / Keyboard Focus Support
        val languages = listOf(
            "es" to "Español",
            "en" to "English",
            "fr" to "Français",
            "pt" to "Português"
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            languages.forEach { (code, name) ->
                val isSelected = state.selectedLanguage == code
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val isFocused by interactionSource.collectIsFocusedAsState()
                val isHighlighted = isHovered || isFocused

                val scale by animateFloatAsState(
                    targetValue = if (isHighlighted) 1.08f else 1.0f,
                    animationSpec = tween(150)
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isSelected -> CloudStreamColors.Primary
                        isHighlighted -> CloudStreamColors.SurfaceElevated
                        else -> CloudStreamColors.SurfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isSelected || isHighlighted) 1.5.dp else 1.dp,
                        color = if (isSelected || isHighlighted) CloudStreamColors.Primary else CloudStreamColors.Divider
                    ),
                    elevation = if (isHighlighted) 4.dp else 0.dp,
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onLanguageSelected(code) }
                        )
                        .focusable(interactionSource = interactionSource)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.body2.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colors.onPrimary else CloudStreamColors.TextPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Step 2: Display layout mode and visual theme selection.
 */
@Composable
private fun LayoutThemeStep(
    state: OnboardingState,
    onLayoutSelected: (String) -> Unit,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        TitleText(
            textRes = Res.string.onboardingStepLayout,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        BodyMutedText(
            textRes = Res.string.onboardingLayoutDesc,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Layout Selection Cards using Design System SelectableOptionCard
        val layoutOptions = listOf(
            "auto" to Res.string.onboardingLayoutAuto,
            "mobile" to Res.string.onboardingLayoutMobile,
            "desktop" to Res.string.onboardingLayoutDesktop,
            "tv" to Res.string.onboardingLayoutTv
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            layoutOptions.forEach { (mode, titleRes) ->
                SelectableOptionCard(
                    titleRes = titleRes,
                    isSelected = state.selectedLayoutMode == mode,
                    onClick = { onLayoutSelected(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Theme Palette Chips
        SubtitleText(
            textRes = Res.string.theme,
            fontWeight = FontWeight.SemiBold,
            color = CloudStreamColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val themes = listOf(AppTheme.AMOLED, AppTheme.DEFAULT, AppTheme.SYSTEM)
            themes.forEach { theme ->
                val isSelected = state.selectedTheme == theme
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val isFocused by interactionSource.collectIsFocusedAsState()
                val isHighlighted = isHovered || isFocused

                val scale by animateFloatAsState(
                    targetValue = if (isHighlighted) 1.08f else 1.0f,
                    animationSpec = tween(150)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        isSelected -> MaterialTheme.colors.primary
                        isHighlighted -> CloudStreamColors.SurfaceVariant
                        else -> CloudStreamColors.Surface
                    },
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onThemeSelected(theme) }
                        )
                        .focusable(interactionSource = interactionSource)
                ) {
                    Text(
                        text = stringResource(theme.displayNameRes),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colors.onPrimary else CloudStreamColors.TextPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Step 3: Starter Extension Repositories selection.
 */
@Composable
private fun PluginsSetupStep(
    state: OnboardingState,
    onToggleRepo: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        TitleText(
            textRes = Res.string.onboardingStepPlugins,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        BodyMutedText(
            textRes = Res.string.onboardingPluginsDesc,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(state.starterRepositories, key = { it.url }) { repo ->
                StarterRepoCard(repo = repo, onToggle = { onToggleRepo(repo.url) })
            }
        }
    }
}

@Composable
private fun StarterRepoCard(
    repo: StarterRepoOption,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.02f else 1.0f,
        animationSpec = tween(150)
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            repo.isSelected -> CloudStreamColors.Primary.copy(alpha = 0.12f)
            isHighlighted -> CloudStreamColors.SurfaceElevated
            else -> CloudStreamColors.SurfaceVariant
        },
        border = BorderStroke(
            width = if (repo.isSelected || isHighlighted) 1.5.dp else 1.dp,
            color = if (repo.isSelected || isHighlighted) CloudStreamColors.Primary else CloudStreamColors.Divider
        ),
        elevation = if (isHighlighted) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Checkbox(
                checked = repo.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CloudStreamColors.Primary,
                    uncheckedColor = CloudStreamColors.TextMuted,
                    checkmarkColor = MaterialTheme.colors.onPrimary
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.body1.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = CloudStreamColors.TextPrimary
                    )
                )
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.caption.copy(color = CloudStreamColors.TextSecondary)
                )
            }
        }
    }
}

/**
 * Step 4: Secure DNS / DoH Provider configuration.
 */
@Composable
private fun DnsSecurityStep(
    state: OnboardingState,
    onDohSelected: (DohProvider) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        TitleText(
            textRes = Res.string.onboardingStepDns,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        BodyMutedText(
            textRes = Res.string.onboardingDnsDesc,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        val providers = listOf(
            DohProvider.CLOUDFLARE,
            DohProvider.ADGUARD,
            DohProvider.GOOGLE,
            DohProvider.QUAD9,
            DohProvider.NONE
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            providers.forEach { provider ->
                val isSelected = state.selectedDohProvider == provider
                val title = if (provider == DohProvider.NONE) stringResource(Res.string.dohDisabled) else provider.displayName
                SelectableOptionCard(
                    title = title,
                    isSelected = isSelected,
                    onClick = { onDohSelected(provider) },
                    subtitle = provider.url
                )
            }
        }
    }
}

/**
 * Step 5: Main Profile Name and Avatar creation.
 */
@Composable
private fun ProfileSetupStep(
    state: OnboardingState,
    onNameChange: (String) -> Unit,
    onAvatarChange: (Int) -> Unit
) {
    val avatarColor = Color(
        AccountViewModel.AVATAR_COLORS.getOrElse(state.profileAvatarIndex) { AccountViewModel.DEFAULT_AVATAR_COLOR }
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        TitleText(
            textRes = Res.string.onboardingStepProfile,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Avatar Preview
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.profileName.take(1).uppercase().ifBlank { "U" },
                style = MaterialTheme.typography.h4.copy(
                    color = MaterialTheme.colors.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name input with CloudStreamTextField
        CloudStreamTextField(
            value = state.profileName,
            onValueChange = onNameChange,
            labelRes = Res.string.profileName,
            placeholderRes = Res.string.profileNamePlaceholder,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Avatar Colors Row with D-Pad focus support
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            AccountViewModel.AVATAR_COLORS.forEachIndexed { index, colorVal ->
                val isSelected = state.profileAvatarIndex == index
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val isFocused by interactionSource.collectIsFocusedAsState()
                val isHighlighted = isHovered || isFocused

                val scale by animateFloatAsState(
                    targetValue = if (isHighlighted) 1.20f else 1.0f,
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Color(colorVal))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onAvatarChange(index) }
                        )
                        .focusable(interactionSource = interactionSource),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom Navigation Action Buttons for the Wizard using PrimaryButton and SecondaryButton.
 */
@Composable
private fun WizardNavigationFooter(
    currentStep: OnboardingStep,
    isLastStep: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back or Skip Button
        if (currentStep.ordinal > 0) {
            SecondaryButton(
                textRes = Res.string.previousStep,
                onClick = onPrevious,
                shape = RoundedCornerShape(20.dp)
            )
        } else {
            SecondaryButton(
                textRes = Res.string.skipSetup,
                onClick = onSkip,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Next or Finish Button
        PrimaryButton(
            textRes = if (isLastStep) Res.string.getStarted else Res.string.nextStep,
            onClick = if (isLastStep) onFinish else onNext,
            shape = RoundedCornerShape(20.dp)
        )
    }
}


