package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.RpCardEntity

@Dao
interface RpCardDAO {
    @Query("SELECT * FROM rp_card ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RpCardEntity>>

    @Query("SELECT * FROM rp_card WHERE id = :id")
    suspend fun getById(id: String): RpCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: RpCardEntity)

    @Update
    suspend fun update(card: RpCardEntity)

    @Delete
    suspend fun delete(card: RpCardEntity)
}
