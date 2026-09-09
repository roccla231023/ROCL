package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 1,
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: Long = System.currentTimeMillis(),
)
