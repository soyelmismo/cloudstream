package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for series subscriptions and episode updates.
 */
@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE accountId = :accountId AND id = :id LIMIT 1")
    suspend fun getSubscription(accountId: Int, id: Int): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE accountId = :accountId AND id = :id LIMIT 1")
    fun getSubscriptionFlow(accountId: Int, id: Int): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE accountId = :accountId ORDER BY latestUpdatedTime DESC")
    suspend fun getAllSubscriptions(accountId: Int): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE accountId = :accountId ORDER BY latestUpdatedTime DESC")
    fun getAllSubscriptionsFlow(accountId: Int): Flow<List<SubscriptionEntity>>

    @Upsert
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE accountId = :accountId AND id = :id")
    suspend fun deleteSubscription(accountId: Int, id: Int)

    @Query("DELETE FROM subscriptions WHERE accountId = :accountId")
    suspend fun clearAccountSubscriptions(accountId: Int)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)
}
