package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.DailyActivityEntity

@Dao
interface DailyActivityDAO {
    @Query("SELECT * FROM daily_activity ORDER BY date ASC")
    fun getAllActivityFlow(): Flow<List<DailyActivityEntity>>

    @Query("SELECT COALESCE(SUM(message_count), 0) FROM daily_activity")
    suspend fun getTotalMessageCount(): Long

    @Query(
        """
        INSERT INTO daily_activity (date, message_count, last_message_time)
        VALUES (:date, 1, :timestamp)
        ON CONFLICT(date) DO UPDATE SET
            message_count = message_count + 1,
            last_message_time = :timestamp
        """
    )
    suspend fun recordActivity(date: String, timestamp: Long = System.currentTimeMillis())

    @Query(
        """
        INSERT OR IGNORE INTO daily_activity (date, message_count, last_message_time)
        VALUES (:date, :count, :timestamp)
        """
    )
    suspend fun insertBackfilledActivityIfMissing(date: String, count: Int, timestamp: Long)

    @Query(
        """
        UPDATE daily_activity
        SET
            message_count = CASE WHEN message_count < :count THEN :count ELSE message_count END,
            last_message_time = CASE WHEN last_message_time < :timestamp THEN :timestamp ELSE last_message_time END
        WHERE date = :date
        """
    )
    suspend fun mergeBackfilledActivity(date: String, count: Int, timestamp: Long)
}
