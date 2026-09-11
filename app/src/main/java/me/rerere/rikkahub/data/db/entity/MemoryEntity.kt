package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MemoryEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String = "",
    @ColumnInfo(name = "embedding", defaultValue = "NULL")
    val embedding: String? = null,
    @ColumnInfo(name = "embedding_model_id", defaultValue = "")
    val embeddingModelId: String? = null,
    @ColumnInfo(name = "type", defaultValue = "0")
    val type: Int = MemoryType.CORE,
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(name = "last_accessed_at", defaultValue = "0")
    val lastAccessedAt: Long = 0,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at", defaultValue = "NULL")
    val updatedAt: Long? = null,
)
