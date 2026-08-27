package com.lagradost.cloudstream3.shared.viewmodels.account

import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepository
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

/**
 * State representing profiles/accounts management.
 */
@Serializable
data class AccountState(
    val accounts: List<AccountEntity> = emptyList(),
    val activeAccountId: Int = 0,
    val isManageMode: Boolean = false,
    val isCreateDialogOpen: Boolean = false,
    val editingAccount: AccountEntity? = null,
    val pinPromptAccount: AccountEntity? = null,
    val pinError: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState {
    val activeAccount: AccountEntity?
        get() = accounts.firstOrNull { it.keyIndex == activeAccountId }
}

/**
 * Events for account and profile management.
 */
sealed class AccountEvent : UiEvent {
    data object LoadAccounts : AccountEvent()
    data class SelectAccount(val account: AccountEntity, val enteredPin: String? = null) : AccountEvent()
    data class CreateAccount(
        val name: String,
        val defaultImageIndex: Int = 0,
        val lockPin: String? = null
    ) : AccountEvent()
    data class UpdateAccount(
        val keyIndex: Int,
        val name: String,
        val defaultImageIndex: Int = 0,
        val lockPin: String? = null
    ) : AccountEvent()
    data class DeleteAccount(val keyIndex: Int) : AccountEvent()
    data object ToggleManageMode : AccountEvent()
    data object OpenCreateDialog : AccountEvent()
    data object CloseCreateDialog : AccountEvent()
    data class OpenEditDialog(val account: AccountEntity) : AccountEvent()
    data object CloseEditDialog : AccountEvent()
    data object DismissPinPrompt : AccountEvent()
    data object ClearError : AccountEvent()
}

/**
 * MVI ViewModel managing user profiles, account switching, avatar customization and security PINs.
 */
class AccountViewModel(
    private val accountRepository: AccountRepository,
    private val preferenceRepository: AppPreferenceRepository,
    initialState: AccountState = AccountState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<AccountState, AccountEvent>(initialState, coroutineContext) {

    companion object {
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val DEFAULT_ACCOUNT_ID = 0
        const val DEFAULT_ACCOUNT_NAME = "Main User"

        val AVATAR_COLORS = listOf(
            0xFF3B82F6L, // Blue
            0xFF8B5CF6L, // Purple
            0xFFEC4899L, // Pink
            0xFFEF4444L, // Red
            0xFFF59E0BL, // Amber
            0xFF10B981L, // Emerald
            0xFF06B6D4L, // Cyan
            0xFF6366F1L  // Indigo
        )
        val DEFAULT_AVATAR_COLOR = AVATAR_COLORS[0]
    }

    init {
        observeAccounts()
        handleEvent(AccountEvent.LoadAccounts)
    }

    private fun observeAccounts() {
        accountRepository.getAllAccountsFlow()
            .onEach { accountsList ->
                val activeId = preferenceRepository.getString(KEY_ACTIVE_ACCOUNT_ID)?.toIntOrNull() ?: DEFAULT_ACCOUNT_ID
                updateState {
                    copy(
                        accounts = accountsList,
                        activeAccountId = if (accountsList.any { it.keyIndex == activeId }) activeId else (accountsList.firstOrNull()?.keyIndex ?: DEFAULT_ACCOUNT_ID)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleEvent(event: AccountEvent) {
        when (event) {
            is AccountEvent.LoadAccounts -> loadAccounts()
            is AccountEvent.SelectAccount -> selectAccount(event.account, event.enteredPin)
            is AccountEvent.CreateAccount -> createAccount(event.name, event.defaultImageIndex, event.lockPin)
            is AccountEvent.UpdateAccount -> updateAccount(event.keyIndex, event.name, event.defaultImageIndex, event.lockPin)
            is AccountEvent.DeleteAccount -> deleteAccount(event.keyIndex)
            is AccountEvent.ToggleManageMode -> updateState { copy(isManageMode = !isManageMode) }
            is AccountEvent.OpenCreateDialog -> updateState { copy(isCreateDialogOpen = true, error = null) }
            is AccountEvent.CloseCreateDialog -> updateState { copy(isCreateDialogOpen = false, error = null) }
            is AccountEvent.OpenEditDialog -> updateState { copy(editingAccount = event.account, error = null) }
            is AccountEvent.CloseEditDialog -> updateState { copy(editingAccount = null, error = null) }
            is AccountEvent.DismissPinPrompt -> updateState { copy(pinPromptAccount = null, pinError = false) }
            is AccountEvent.ClearError -> updateState { copy(error = null, pinError = false) }
        }
    }

    private fun loadAccounts() {
        launchSafeJob(
            key = "load_accounts",
            onError = { t -> updateState { copy(isLoading = false, error = t.message) } }
        ) {
            updateState { copy(isLoading = true) }
            var accountsList = accountRepository.getAllAccounts()
            if (accountsList.isEmpty()) {
                // Seed default main profile if brand new database
                val defaultAccount = AccountEntity(
                    keyIndex = DEFAULT_ACCOUNT_ID,
                    name = DEFAULT_ACCOUNT_NAME,
                    defaultImageIndex = 0
                )
                accountRepository.saveAccount(defaultAccount)
                accountsList = listOf(defaultAccount)
            }
            val activeId = preferenceRepository.getString(KEY_ACTIVE_ACCOUNT_ID)?.toIntOrNull() ?: DEFAULT_ACCOUNT_ID
            updateState {
                copy(
                    accounts = accountsList,
                    activeAccountId = if (accountsList.any { it.keyIndex == activeId }) activeId else (accountsList.firstOrNull()?.keyIndex ?: DEFAULT_ACCOUNT_ID),
                    isLoading = false
                )
            }
        }
    }

    private fun selectAccount(account: AccountEntity, enteredPin: String?) {
        launchSafeJob(key = "select_account") job@{
            if (account.lockPin != null && account.lockPin.isNotBlank()) {
                if (enteredPin == null) {
                    // Require PIN input
                    updateState { copy(pinPromptAccount = account, pinError = false) }
                    return@job
                } else if (enteredPin != account.lockPin) {
                    // Incorrect PIN
                    updateState { copy(pinError = true) }
                    return@job
                }
            }

            // PIN verified or not required
            preferenceRepository.setString(KEY_ACTIVE_ACCOUNT_ID, account.keyIndex.toString())
            updateState {
                copy(
                    activeAccountId = account.keyIndex,
                    pinPromptAccount = null,
                    pinError = false,
                    isManageMode = false
                )
            }
        }
    }

    private fun createAccount(name: String, defaultImageIndex: Int, lockPin: String?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            updateState { copy(error = "Profile name cannot be empty") }
            return
        }

        launchSafeJob(
            key = "create_account",
            onError = { t -> updateState { copy(error = t.message) } }
        ) {
            val existing = accountRepository.getAllAccounts()
            val nextKey = (existing.maxOfOrNull { it.keyIndex } ?: -1) + 1
            val sanitizedPin = lockPin?.trim()?.ifBlank { null }
            val newAccount = AccountEntity(
                keyIndex = nextKey,
                name = trimmed,
                defaultImageIndex = defaultImageIndex.coerceIn(0, AVATAR_COLORS.lastIndex),
                lockPin = sanitizedPin
            )
            accountRepository.saveAccount(newAccount)
            preferenceRepository.setString(KEY_ACTIVE_ACCOUNT_ID, nextKey.toString())
            updateState {
                copy(
                    isCreateDialogOpen = false,
                    activeAccountId = nextKey,
                    error = null
                )
            }
        }
    }

    private fun updateAccount(keyIndex: Int, name: String, defaultImageIndex: Int, lockPin: String?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            updateState { copy(error = "Profile name cannot be empty") }
            return
        }

        launchSafeJob(
            key = "update_account",
            onError = { t -> updateState { copy(error = t.message) } }
        ) job@{
            val existing = accountRepository.getAccount(keyIndex) ?: return@job
            val sanitizedPin = lockPin?.trim()?.ifBlank { null }
            val updated = existing.copy(
                name = trimmed,
                defaultImageIndex = defaultImageIndex.coerceIn(0, AVATAR_COLORS.lastIndex),
                lockPin = sanitizedPin
            )
            accountRepository.saveAccount(updated)
            updateState {
                copy(
                    editingAccount = null,
                    error = null
                )
            }
        }
    }

    private fun deleteAccount(keyIndex: Int) {
        launchSafeJob(
            key = "delete_account",
            onError = { t -> updateState { copy(error = t.message) } }
        ) job@{
            val existing = accountRepository.getAllAccounts()
            if (existing.size <= 1) {
                updateState { copy(error = "Cannot delete the only remaining profile") }
                return@job
            }

            accountRepository.deleteAccount(keyIndex)
            val remaining = accountRepository.getAllAccounts()
            val nextActive = if (currentState.activeAccountId == keyIndex) {
                remaining.firstOrNull()?.keyIndex ?: DEFAULT_ACCOUNT_ID
            } else {
                currentState.activeAccountId
            }
            preferenceRepository.setString(KEY_ACTIVE_ACCOUNT_ID, nextActive.toString())
            updateState {
                copy(
                    accounts = remaining,
                    activeAccountId = nextActive,
                    editingAccount = null,
                    error = null
                )
            }
        }
    }
}
