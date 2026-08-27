package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Account management.
 */
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY keyIndex ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY keyIndex ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE keyIndex = :keyIndex LIMIT 1")
    suspend fun getAccountById(keyIndex: Int): AccountEntity?

    @Query("SELECT * FROM accounts WHERE keyIndex = :keyIndex LIMIT 1")
    fun getAccountByIdFlow(keyIndex: Int): Flow<AccountEntity?>

    @Upsert
    suspend fun upsertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE keyIndex = :keyIndex")
    suspend fun deleteAccountById(keyIndex: Int)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int
}
