package com.lagradost.cloudstream3.shared.viewmodels.onboarding

import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepository
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

/**
 * Sequential steps in the Onboarding Setup Wizard.
 */
@Serializable
enum class OnboardingStep(val stepIndex: Int) {
    WELCOME_LANGUAGE(0),
    LAYOUT_THEME(1),
    PLUGINS_REPOSITORIES(2),
    DNS_SECURITY(3),
    PROFILE_SETUP(4)
}

/**
 * Pre-configured starter repository option for quick setup.
 */
@Serializable
data class StarterRepoOption(
    val name: String,
    val description: String,
    val url: String,
    val isSelected: Boolean = true,
    val language: String = "Multi"
)

/**
 * State representing the Onboarding Setup Wizard.
 */
@Serializable
data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME_LANGUAGE,
    val selectedLanguage: String = "es",
    val selectedLayoutMode: String = "auto", // auto, mobile, desktop, tv
    val selectedTheme: AppTheme = AppTheme.AMOLED,
    val selectedDohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val starterRepositories: List<StarterRepoOption> = listOf(
        StarterRepoOption(
            name = "CloudStream Official & Community",
            description = "Main English, Multi-language and Anime scrapers",
            url = "https://raw.githubusercontent.com/cloudstream/builds/repo.json",
            isSelected = true,
            language = "en"
        ),
        StarterRepoOption(
            name = "Spanish Providers (Hexated & Cinedata)",
            description = "Películas y series en Español (Castellano y Latino)",
            url = "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/repo.json",
            isSelected = true,
            language = "es"
        ),
        StarterRepoOption(
            name = "Anime & Manga Repositories",
            description = "Specialized anime, sub/dub and raw streaming sources",
            url = "https://raw.githubusercontent.com/stormunblessed/stormunblessed-cs3/builds/repo.json",
            isSelected = true,
            language = "all"
        )
    ),
    val profileName: String = "User",
    val profileAvatarIndex: Int = 0,
    val isCompleting: Boolean = false,
    val hasCompleted: Boolean = false,
    val error: String? = null
) : UiState

/**
 * Events for the Onboarding Setup Wizard.
 */
sealed class OnboardingEvent : UiEvent {
    data object NextStep : OnboardingEvent()
    data object PreviousStep : OnboardingEvent()
    data class GoToStep(val step: OnboardingStep) : OnboardingEvent()
    data class SelectLanguage(val langCode: String) : OnboardingEvent()
    data class SelectLayoutMode(val mode: String) : OnboardingEvent()
    data class SelectTheme(val theme: AppTheme) : OnboardingEvent()
    data class SelectDohProvider(val doh: DohProvider) : OnboardingEvent()
    data class ToggleStarterRepo(val url: String) : OnboardingEvent()
    data class SetProfileName(val name: String) : OnboardingEvent()
    data class SetProfileAvatar(val index: Int) : OnboardingEvent()
    data object CompleteOnboarding : OnboardingEvent()
    data object SkipOnboarding : OnboardingEvent()
}

/**
 * MVI ViewModel controlling user first-run experience, initial customization and profile setup.
 */
class OnboardingViewModel(
    private val preferenceRepository: AppPreferenceRepository,
    private val accountRepository: AccountRepository,
    private val pluginsRepository: PluginsRepository? = null,
    initialState: OnboardingState = OnboardingState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<OnboardingState, OnboardingEvent>(initialState, coroutineContext) {

    companion object {
        const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        const val KEY_APP_LAYOUT_MODE = "app_layout_mode"
        const val KEY_APP_LANGUAGE = "app_language"
    }

    override fun handleEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.NextStep -> advanceStep()
            is OnboardingEvent.PreviousStep -> retreatStep()
            is OnboardingEvent.GoToStep -> updateState { copy(currentStep = event.step) }
            is OnboardingEvent.SelectLanguage -> updateState { copy(selectedLanguage = event.langCode) }
            is OnboardingEvent.SelectLayoutMode -> updateState { copy(selectedLayoutMode = event.mode) }
            is OnboardingEvent.SelectTheme -> updateState { copy(selectedTheme = event.theme) }
            is OnboardingEvent.SelectDohProvider -> updateState { copy(selectedDohProvider = event.doh) }
            is OnboardingEvent.ToggleStarterRepo -> toggleStarterRepo(event.url)
            is OnboardingEvent.SetProfileName -> updateState { copy(profileName = event.name) }
            is OnboardingEvent.SetProfileAvatar -> updateState { copy(profileAvatarIndex = event.index) }
            is OnboardingEvent.CompleteOnboarding -> finishOnboarding()
            is OnboardingEvent.SkipOnboarding -> skipOnboarding()
        }
    }

    private fun advanceStep() {
        val nextOrdinal = currentState.currentStep.ordinal + 1
        if (nextOrdinal < OnboardingStep.entries.size) {
            updateState { copy(currentStep = OnboardingStep.entries[nextOrdinal]) }
        } else {
            finishOnboarding()
        }
    }

    private fun retreatStep() {
        val prevOrdinal = currentState.currentStep.ordinal - 1
        if (prevOrdinal >= 0) {
            updateState { copy(currentStep = OnboardingStep.entries[prevOrdinal]) }
        }
    }

    private fun toggleStarterRepo(url: String) {
        updateState {
            copy(
                starterRepositories = starterRepositories.map { repo ->
                    if (repo.url == url) repo.copy(isSelected = !repo.isSelected) else repo
                }
            )
        }
    }

    private fun finishOnboarding() {
        launchSafeJob(
            key = "finish_onboarding",
            onError = { t -> updateState { copy(isCompleting = false, error = t.message) } }
        ) {
            updateState { copy(isCompleting = true) }
            // 1. Save language
            preferenceRepository.setString(KEY_APP_LANGUAGE, currentState.selectedLanguage)

            // 2. Save layout mode
            preferenceRepository.setString(KEY_APP_LAYOUT_MODE, currentState.selectedLayoutMode)

            // 3. Save Theme & DoH Provider
            preferenceRepository.setString(AppSettingsViewModel.KEY_APP_THEME, currentState.selectedTheme.key)
            preferenceRepository.setString(AppSettingsViewModel.KEY_DOH_PROVIDER, currentState.selectedDohProvider.id.toString())

            // 4. Save/create main profile
            val initialName = currentState.profileName.trim().ifBlank { "User" }
            val accounts = accountRepository.getAllAccounts()
            val activeId = if (accounts.isEmpty()) {
                val newAcc = AccountEntity(
                    keyIndex = 0,
                    name = initialName,
                    defaultImageIndex = currentState.profileAvatarIndex
                )
                accountRepository.saveAccount(newAcc)
                0
            } else {
                val first = accounts.first()
                accountRepository.saveAccount(
                    first.copy(
                        name = initialName,
                        defaultImageIndex = currentState.profileAvatarIndex
                    )
                )
                first.keyIndex
            }
            preferenceRepository.setString(AccountViewModel.KEY_ACTIVE_ACCOUNT_ID, activeId.toString())

            // 5. Install / add starter repositories
            pluginsRepository?.let { repo ->
                val selectedRepos = currentState.starterRepositories.filter { it.isSelected }
                for (starter in selectedRepos) {
                    try {
                        repo.addRepository(
                            com.lagradost.cloudstream3.shared.viewmodels.settings.PluginRepositoryItem(
                                name = starter.name,
                                url = starter.url,
                                isRemovable = true
                            )
                        )
                    } catch (_: Throwable) {}
                }
            }

            // 6. Mark onboarding completed
            preferenceRepository.setString(KEY_HAS_COMPLETED_ONBOARDING, "true")

            updateState { copy(isCompleting = false, hasCompleted = true) }
        }
    }

    private fun skipOnboarding() {
        launchSafeJob(
            key = "skip_onboarding",
            onError = { t -> updateState { copy(isCompleting = false, error = t.message) } }
        ) {
            updateState { copy(isCompleting = true) }
            preferenceRepository.setString(KEY_HAS_COMPLETED_ONBOARDING, "true")
            updateState { copy(isCompleting = false, hasCompleted = true) }
        }
    }
}
