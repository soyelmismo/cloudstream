package com.lagradost.cloudstream3.shared.viewmodels.account

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.shared.mvi.BaseViewModel
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepository
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

@Immutable
@Serializable
data class AccountState(
    val accounts: ImmutableList<AccountEntity> = persistentListOf(),
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

class AccountViewModel(
    private val accountRepository: AccountRepository,
    private val preferenceRepository: AppPreferenceRepository,
    initialState: AccountState = AccountState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : BaseViewModel(coroutineContext) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<AccountState> = _state.asStateFlow()
    val currentState: AccountState
        get() = _state.value

    protected fun updateState(reducer: AccountState.() -> AccountState) {
        _state.update { it.reducer() }
    }

    companion object {
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val DEFAULT_ACCOUNT_ID = 0
        const val DEFAULT_ACCOUNT_NAME = "Main User"

        val AVATAR_COLORS = listOf(
            0xFF3B82F6L,
            0xFF8B5CF6L,
            0xFFEC4899L,
            0xFFEF4444L,
            0xFFF59E0BL,
            0xFF10B981L,
            0xFF06B6D4L,
            0xFF6366F1L
        )
        val DEFAULT_AVATAR_COLOR = AVATAR_COLORS[0]
    }

    init {
        observeAccounts()
        loadAccounts()
    }

    private fun observeAccounts() {
        accountRepository.getAllAccountsFlow()
            .onEach { accountsList ->
                val activeId = preferenceRepository.getString(KEY_ACTIVE_ACCOUNT_ID)?.toIntOrNull() ?: DEFAULT_ACCOUNT_ID
                updateState {
                    copy(
                        accounts = accountsList.toImmutableList(),
                        activeAccountId = if (accountsList.any { it.keyIndex == activeId }) activeId else (accountsList.firstOrNull()?.keyIndex ?: DEFAULT_ACCOUNT_ID)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun toggleManageMode() {
        updateState { copy(isManageMode = !isManageMode) }
    }

    fun openCreateDialog() {
        updateState { copy(isCreateDialogOpen = true, error = null) }
    }

    fun closeCreateDialog() {
        updateState { copy(isCreateDialogOpen = false, error = null) }
    }

    fun openEditDialog(account: AccountEntity) {
        updateState { copy(editingAccount = account, error = null) }
    }

    fun closeEditDialog() {
        updateState { copy(editingAccount = null, error = null) }
    }

    fun dismissPinPrompt() {
        updateState { copy(pinPromptAccount = null, pinError = false) }
    }

    fun clearError() {
        updateState { copy(error = null, pinError = false) }
    }

    fun loadAccounts() {
        launchSafeJob(
            key = "load_accounts",
            onError = { t -> updateState { copy(isLoading = false, error = t.message) } }
        ) {
            updateState { copy(isLoading = true) }
            val allAccounts = accountRepository.getAllAccounts()
            val accountsList = if (allAccounts.isEmpty()) {
                val defaultAccount = AccountEntity(
                    keyIndex = DEFAULT_ACCOUNT_ID,
                    name = DEFAULT_ACCOUNT_NAME,
                    defaultImageIndex = 0
                )
                accountRepository.saveAccount(defaultAccount)
                persistentListOf(defaultAccount)
            } else {
                allAccounts.toImmutableList()
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

    fun selectAccount(account: AccountEntity, enteredPin: String? = null) {
        launchSafeJob(key = "select_account") job@{
            if (account.lockPin != null && account.lockPin.isNotBlank()) {
                if (enteredPin == null) {
                    updateState { copy(pinPromptAccount = account, pinError = false) }
                    return@job
                } else if (enteredPin != account.lockPin) {
                    updateState { copy(pinError = true) }
                    return@job
                }
            }

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

    fun createAccount(name: String, defaultImageIndex: Int = 0, lockPin: String? = null) {
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

    fun updateAccount(keyIndex: Int, name: String, defaultImageIndex: Int = 0, lockPin: String? = null) {
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

    fun deleteAccount(keyIndex: Int) {
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
                    accounts = remaining.toImmutableList(),
                    activeAccountId = nextActive,
                    editingAccount = null,
                    error = null
                )
            }
        }
    }
}
