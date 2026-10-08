package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rp_turn",
    indices = [Index("session_id"), Index("branch_id")],
)
data class RpTurnEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("session_id") val sessionId: String,
    @ColumnInfo("branch_id") val branchId: String,
    val input: String,
    val status: String,
    @ColumnInfo("outcome_json") val outcomeJson: String,
    @ColumnInfo("review_json") val reviewJson: String,
    val narrative: String,
    @ColumnInfo("state_before_json", defaultValue = "") val stateBeforeJson: String = "",
    @ColumnInfo("state_after_json") val stateAfterJson: String,
    @ColumnInfo("event_json", defaultValue = "") val eventJson: String = "",
    val error: String?,
    @ColumnInfo("created_at") val createdAt: Long,
)
