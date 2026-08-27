package com.lagradost.cloudstream3.shared.viewmodels.account

import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepository
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakeAppPreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeAccountRepository : AccountRepository {
    private val accounts = mutableListOf<AccountEntity>()
    private val flow = MutableStateFlow<List<AccountEntity>>(emptyList())

    override fun getAllAccountsFlow(): Flow<List<AccountEntity>> = flow

    override suspend fun getAllAccounts(): List<AccountEntity> = accounts.toList()

    override suspend fun getAccount(keyIndex: Int): AccountEntity? =
        accounts.firstOrNull { it.keyIndex == keyIndex }

    override suspend fun saveAccount(account: AccountEntity) {
        val index = accounts.indexOfFirst { it.keyIndex == account.keyIndex }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }
        flow.value = accounts.toList()
    }

    override suspend fun deleteAccount(keyIndex: Int) {
        accounts.removeAll { it.keyIndex == keyIndex }
        flow.value = accounts.toList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @Test
    fun testDefaultAccountSeeding() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val accountRepo = FakeAccountRepository()
        val prefRepo = FakeAppPreferenceRepository()

        val viewModel = AccountViewModel(
            accountRepository = accountRepo,
            preferenceRepository = prefRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.accounts.size)
        assertEquals("Main User", state.accounts.first().name)
        assertEquals(0, state.activeAccountId)
        assertNotNull(state.activeAccount)
    }

    @Test
    fun testCreateAccount() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val accountRepo = FakeAccountRepository()
        val prefRepo = FakeAppPreferenceRepository()

        val viewModel = AccountViewModel(
            accountRepository = accountRepo,
            preferenceRepository = prefRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.handleEvent(AccountEvent.CreateAccount(name = "Living Room", defaultImageIndex = 2, lockPin = "1234"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.accounts.size)
        val newAccount = state.accounts.first { it.name == "Living Room" }
        assertEquals(2, newAccount.defaultImageIndex)
        assertEquals("1234", newAccount.lockPin)
        assertEquals(newAccount.keyIndex, state.activeAccountId)
    }

    @Test
    fun testSelectAccountWithAndWithoutPin() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val accountRepo = FakeAccountRepository()
        val prefRepo = FakeAppPreferenceRepository()

        val viewModel = AccountViewModel(
            accountRepository = accountRepo,
            preferenceRepository = prefRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        // Create locked profile
        viewModel.handleEvent(AccountEvent.CreateAccount(name = "Secret Profile", defaultImageIndex = 3, lockPin = "9876"))
        advanceUntilIdle()

        val mainUser = viewModel.state.value.accounts.first { it.name == "Main User" }
        val secretUser = viewModel.state.value.accounts.first { it.name == "Secret Profile" }

        // Switch to main user (no PIN)
        viewModel.handleEvent(AccountEvent.SelectAccount(mainUser))
        advanceUntilIdle()
        assertEquals(mainUser.keyIndex, viewModel.state.value.activeAccountId)
        assertNull(viewModel.state.value.pinPromptAccount)

        // Switch to secret user without PIN -> should prompt
        viewModel.handleEvent(AccountEvent.SelectAccount(secretUser))
        advanceUntilIdle()
        assertEquals(secretUser, viewModel.state.value.pinPromptAccount)
        assertFalse(viewModel.state.value.pinError)

        // Enter wrong PIN
        viewModel.handleEvent(AccountEvent.SelectAccount(secretUser, enteredPin = "0000"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.pinError)
        assertEquals(mainUser.keyIndex, viewModel.state.value.activeAccountId)

        // Enter correct PIN
        viewModel.handleEvent(AccountEvent.SelectAccount(secretUser, enteredPin = "9876"))
        advanceUntilIdle()
        assertEquals(secretUser.keyIndex, viewModel.state.value.activeAccountId)
        assertNull(viewModel.state.value.pinPromptAccount)
        assertFalse(viewModel.state.value.pinError)
    }

    @Test
    fun testUpdateAndDeleteAccount() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val accountRepo = FakeAccountRepository()
        val prefRepo = FakeAppPreferenceRepository()

        val viewModel = AccountViewModel(
            accountRepository = accountRepo,
            preferenceRepository = prefRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.handleEvent(AccountEvent.CreateAccount(name = "Guest", defaultImageIndex = 1))
        advanceUntilIdle()

        val guestAccount = viewModel.state.value.accounts.first { it.name == "Guest" }

        // Update
        viewModel.handleEvent(AccountEvent.UpdateAccount(guestAccount.keyIndex, name = "Guest User Updated", defaultImageIndex = 4))
        advanceUntilIdle()

        val updated = viewModel.state.value.accounts.first { it.keyIndex == guestAccount.keyIndex }
        assertEquals("Guest User Updated", updated.name)
        assertEquals(4, updated.defaultImageIndex)

        // Delete
        viewModel.handleEvent(AccountEvent.DeleteAccount(guestAccount.keyIndex))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.accounts.size)
        assertFalse(viewModel.state.value.accounts.any { it.keyIndex == guestAccount.keyIndex })

        // Cannot delete only remaining profile
        val mainUser = viewModel.state.value.accounts.first()
        viewModel.handleEvent(AccountEvent.DeleteAccount(mainUser.keyIndex))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.accounts.size)
        assertNotNull(viewModel.state.value.error)
    }
}
