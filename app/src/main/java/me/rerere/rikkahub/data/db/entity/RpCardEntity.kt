package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rp_card")
data class RpCardEntity(
    @PrimaryKey val id: String,
    val version: Int,
    @ColumnInfo("card_json") val cardJson: String,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)
