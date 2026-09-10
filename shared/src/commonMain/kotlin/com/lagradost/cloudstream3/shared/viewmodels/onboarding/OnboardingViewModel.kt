package com.lagradost.cloudstream3.shared.viewmodels.onboarding

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.shared.mvi.BaseViewModel
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepository
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginRepositoryItem
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

@Serializable
enum class OnboardingStep(val stepIndex: Int) {
    WELCOME_LANGUAGE(0),
    LAYOUT_THEME(1),
    PLUGINS_REPOSITORIES(2),
    DNS_SECURITY(3),
    PROFILE_SETUP(4)
}

@Immutable
@Serializable
data class StarterRepoOption(
    val name: String,
    val description: String,
    val url: String,
    val isSelected: Boolean = true,
    val language: String = "Multi"
)

@Immutable
@Serializable
data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME_LANGUAGE,
    val selectedLanguage: String = "es",
    val selectedLayoutMode: String = "auto",
    val selectedTheme: AppTheme = AppTheme.AMOLED,
    val selectedDohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val starterRepositories: ImmutableList<StarterRepoOption> = persistentListOf(
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

class OnboardingViewModel(
    private val preferenceRepository: AppPreferenceRepository,
    private val accountRepository: AccountRepository,
    private val pluginsRepository: PluginsRepository? = null,
    initialState: OnboardingState = OnboardingState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : BaseViewModel(coroutineContext) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    val currentState: OnboardingState
        get() = _state.value

    protected fun updateState(reducer: OnboardingState.() -> OnboardingState) {
        _state.update { it.reducer() }
    }

    companion object {
        const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        const val KEY_APP_LAYOUT_MODE = "app_layout_mode"
        const val KEY_APP_LANGUAGE = "app_language"
    }

    fun nextStep() = advanceStep()
    fun previousStep() = retreatStep()

    fun advanceStep() {
        val nextOrdinal = currentState.currentStep.ordinal + 1
        if (nextOrdinal < OnboardingStep.entries.size) {
            updateState { copy(currentStep = OnboardingStep.entries[nextOrdinal]) }
        } else {
            finishOnboarding()
        }
    }

    fun retreatStep() {
        val prevOrdinal = currentState.currentStep.ordinal - 1
        if (prevOrdinal >= 0) {
            updateState { copy(currentStep = OnboardingStep.entries[prevOrdinal]) }
        }
    }

    fun goToStep(step: OnboardingStep) {
        updateState { copy(currentStep = step) }
    }

    fun selectLanguage(langCode: String) {
        updateState { copy(selectedLanguage = langCode) }
    }

    fun selectLayoutMode(mode: String) {
        updateState { copy(selectedLayoutMode = mode) }
    }

    fun selectTheme(theme: AppTheme) {
        updateState { copy(selectedTheme = theme) }
    }

    fun selectDohProvider(doh: DohProvider) {
        updateState { copy(selectedDohProvider = doh) }
    }

    fun setProfileName(name: String) {
        updateState { copy(profileName = name) }
    }

    fun setProfileAvatar(index: Int) {
        updateState { copy(profileAvatarIndex = index) }
    }

    fun completeOnboarding() = finishOnboarding()

    fun toggleStarterRepo(url: String) {
        updateState {
            copy(
                starterRepositories = starterRepositories.map { repo ->
                    if (repo.url == url) repo.copy(isSelected = !repo.isSelected) else repo
                }.toImmutableList()
            )
        }
    }

    fun finishOnboarding() {
        launchSafeJob(
            key = "finish_onboarding",
            onError = { t -> updateState { copy(isCompleting = false, error = t.message) } }
        ) {
            updateState { copy(isCompleting = true) }
            preferenceRepository.setString(KEY_APP_LANGUAGE, currentState.selectedLanguage)
            preferenceRepository.setString(KEY_APP_LAYOUT_MODE, currentState.selectedLayoutMode)
            preferenceRepository.setString(AppSettingsViewModel.KEY_APP_THEME, currentState.selectedTheme.key)
            preferenceRepository.setString(AppSettingsViewModel.KEY_DOH_PROVIDER, currentState.selectedDohProvider.id.toString())

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

            pluginsRepository?.let { repo ->
                val selectedRepos = currentState.starterRepositories.filter { it.isSelected }
                for (starter in selectedRepos) {
                    try {
                        repo.addRepository(
                            PluginRepositoryItem(
                                name = starter.name,
                                url = starter.url,
                                isRemovable = true
                            )
                        )
                    } catch (_: Throwable) {}
                }
            }

            preferenceRepository.setString(KEY_HAS_COMPLETED_ONBOARDING, "true")
            updateState { copy(isCompleting = false, hasCompleted = true) }
        }
    }

    fun skipOnboarding() {
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
