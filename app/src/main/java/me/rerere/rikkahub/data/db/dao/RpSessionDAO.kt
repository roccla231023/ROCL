package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.RpSessionEntity

@Dao
interface RpSessionDAO {
    @Query("SELECT * FROM rp_session ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RpSessionEntity>>

    @Query("SELECT * FROM rp_session WHERE id = :id")
    fun observeById(id: String): Flow<RpSessionEntity?>

    @Query("SELECT * FROM rp_session WHERE id = :id")
    suspend fun getById(id: String): RpSessionEntity?

    @Query("SELECT * FROM rp_session WHERE conversation_id = :conversationId")
    suspend fun getByConversationId(conversationId: String): RpSessionEntity?

    @Query("DELETE FROM rp_session WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RpSessionEntity)

    @Update
    suspend fun update(session: RpSessionEntity)

    @Delete
    suspend fun delete(session: RpSessionEntity)
}
