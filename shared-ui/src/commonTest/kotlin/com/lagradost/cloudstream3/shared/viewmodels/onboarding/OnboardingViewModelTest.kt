package com.lagradost.cloudstream3.shared.viewmodels.onboarding

import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepository
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel
import com.lagradost.cloudstream3.shared.viewmodels.account.FakeAccountRepository
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakeAppPreferenceRepository
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakePluginsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @Test
    fun testWizardStepTransitions() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val prefRepo = FakeAppPreferenceRepository()
        val accountRepo = FakeAccountRepository()
        val pluginsRepo = FakePluginsRepository()

        val viewModel = OnboardingViewModel(
            preferenceRepository = prefRepo,
            accountRepository = accountRepo,
            pluginsRepository = pluginsRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        assertEquals(OnboardingStep.WELCOME_LANGUAGE, viewModel.state.value.currentStep)

        // Forward steps
        viewModel.nextStep()
        assertEquals(OnboardingStep.LAYOUT_THEME, viewModel.state.value.currentStep)

        viewModel.nextStep()
        assertEquals(OnboardingStep.PLUGINS_REPOSITORIES, viewModel.state.value.currentStep)

        // Backward step
        viewModel.previousStep()
        assertEquals(OnboardingStep.LAYOUT_THEME, viewModel.state.value.currentStep)

        // Direct navigation
        viewModel.goToStep(OnboardingStep.PROFILE_SETUP)
        assertEquals(OnboardingStep.PROFILE_SETUP, viewModel.state.value.currentStep)
    }

    @Test
    fun testCustomizationSelections() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val prefRepo = FakeAppPreferenceRepository()
        val accountRepo = FakeAccountRepository()

        val viewModel = OnboardingViewModel(
            preferenceRepository = prefRepo,
            accountRepository = accountRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.selectLanguage("en")
        assertEquals("en", viewModel.state.value.selectedLanguage)

        viewModel.selectLayoutMode("desktop")
        assertEquals("desktop", viewModel.state.value.selectedLayoutMode)

        viewModel.selectTheme(AppTheme.AMOLED)
        assertEquals(AppTheme.AMOLED, viewModel.state.value.selectedTheme)

        viewModel.selectDohProvider(DohProvider.ADGUARD)
        assertEquals(DohProvider.ADGUARD, viewModel.state.value.selectedDohProvider)

        val firstRepoUrl = viewModel.state.value.starterRepositories.first().url
        viewModel.toggleStarterRepo(firstRepoUrl)
        assertFalse(viewModel.state.value.starterRepositories.first().isSelected)

        viewModel.setProfileName("Cinema Room")
        assertEquals("Cinema Room", viewModel.state.value.profileName)

        viewModel.setProfileAvatar(5)
        assertEquals(5, viewModel.state.value.profileAvatarIndex)
    }

    @Test
    fun testCompleteOnboarding() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val prefRepo = FakeAppPreferenceRepository()
        val accountRepo = FakeAccountRepository()
        val pluginsRepo = FakePluginsRepository()

        val viewModel = OnboardingViewModel(
            preferenceRepository = prefRepo,
            accountRepository = accountRepo,
            pluginsRepository = pluginsRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.selectLanguage("es")
        viewModel.selectLayoutMode("mobile")
        viewModel.selectTheme(AppTheme.AMOLED)
        viewModel.selectDohProvider(DohProvider.CLOUDFLARE)
        viewModel.setProfileName("Rot Streamer")
        viewModel.setProfileAvatar(2)

        viewModel.completeOnboarding()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasCompleted)
        assertFalse(state.isCompleting)

        // Verify persistence
        assertEquals("es", prefRepo.getString(OnboardingViewModel.KEY_APP_LANGUAGE))
        assertEquals("mobile", prefRepo.getString(OnboardingViewModel.KEY_APP_LAYOUT_MODE))
        assertEquals(AppTheme.AMOLED.key, prefRepo.getString(AppSettingsViewModel.KEY_APP_THEME))
        assertEquals(DohProvider.CLOUDFLARE.id.toString(), prefRepo.getString(AppSettingsViewModel.KEY_DOH_PROVIDER))
        assertEquals("true", prefRepo.getString(OnboardingViewModel.KEY_HAS_COMPLETED_ONBOARDING))

        val accounts = accountRepo.getAllAccounts()
        assertEquals(1, accounts.size)
        assertEquals("Rot Streamer", accounts.first().name)
        assertEquals(2, accounts.first().defaultImageIndex)
    }

    @Test
    fun testSkipOnboarding() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val prefRepo = FakeAppPreferenceRepository()
        val accountRepo = FakeAccountRepository()

        val viewModel = OnboardingViewModel(
            preferenceRepository = prefRepo,
            accountRepository = accountRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.skipOnboarding()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasCompleted)
        assertEquals("true", prefRepo.getString(OnboardingViewModel.KEY_HAS_COMPLETED_ONBOARDING))
    }
}
