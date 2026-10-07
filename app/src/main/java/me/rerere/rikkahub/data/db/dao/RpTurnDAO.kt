package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.RpTurnEntity

@Dao
interface RpTurnDAO {
    @Query("SELECT * FROM rp_turn WHERE session_id = :sessionId AND branch_id = :branchId ORDER BY created_at ASC")
    fun observeForBranch(sessionId: String, branchId: String): Flow<List<RpTurnEntity>>

    @Query("SELECT * FROM rp_turn WHERE session_id = :sessionId AND branch_id = :branchId ORDER BY created_at ASC")
    suspend fun getForBranch(sessionId: String, branchId: String): List<RpTurnEntity>

    @Query("SELECT * FROM rp_turn WHERE id = :id")
    suspend fun getById(id: String): RpTurnEntity?

    @Query("DELETE FROM rp_turn WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(turn: RpTurnEntity)

    @Update
    suspend fun update(turn: RpTurnEntity)
}
