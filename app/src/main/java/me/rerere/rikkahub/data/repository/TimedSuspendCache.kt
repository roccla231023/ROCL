package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TimedSuspendCache<T>(
    private val maxAgeMs: Long,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    internal data class Entry<T>(
        val value: T,
        val updatedAtMs: Long,
    )

    private val mutex = Mutex()

    @Volatile
    private var entry: Entry<T>? = null

    fun peek(): Entry<T>? = entry

    fun invalidate() {
        entry = null
    }

    suspend fun get(
        forceRefresh: Boolean = false,
        loader: suspend () -> T,
    ): T {
        val cached = entry
        val now = nowMs()
        if (!forceRefresh && cached != null && now - cached.updatedAtMs <= maxAgeMs) {
            return cached.value
        }

        return mutex.withLock {
            val cached2 = entry
            val now2 = nowMs()
            if (!forceRefresh && cached2 != null && now2 - cached2.updatedAtMs <= maxAgeMs) {
                return@withLock cached2.value
            }

            val fresh = loader()
            entry = Entry(value = fresh, updatedAtMs = nowMs())
            fresh
        }
    }
}
