package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entity representing an account/profile in CloudStream.
 */
@Entity(tableName = "accounts")
@Serializable
data class AccountEntity(
    @PrimaryKey val keyIndex: Int,
    val name: String,
    val customImage: String? = null,
    val defaultImageIndex: Int = 0,
    val lockPin: String? = null
)
