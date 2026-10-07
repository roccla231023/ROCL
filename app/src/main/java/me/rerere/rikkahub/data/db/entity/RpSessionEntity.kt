package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rp_session")
data class RpSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("card_id") val cardId: String,
    @ColumnInfo("conversation_id") val conversationId: String,
    @ColumnInfo("card_json") val cardJson: String,
    val title: String,
    @ColumnInfo("active_branch_id") val activeBranchId: String,
    val revision: Long,
    @ColumnInfo("state_json") val stateJson: String,
    val status: String,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)
