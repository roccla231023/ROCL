package me.rerere.rikkahub.data.repository

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedSuspendCacheTest {
    @Test
    fun `get should memoize within ttl`() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TimedSuspendCache<Int>(
            maxAgeMs = 10_000L,
            nowMs = { 0L },
        )

        val v1 = cache.get { calls.incrementAndGet() }
        val v2 = cache.get { calls.incrementAndGet() }

        assertEquals(1, calls.get())
        assertEquals(v1, v2)
    }

    @Test
    fun `get should refresh when forced`() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TimedSuspendCache<Int>(
            maxAgeMs = 10_000L,
            nowMs = { 0L },
        )

        val v1 = cache.get { calls.incrementAndGet() }
        val v2 = cache.get(forceRefresh = true) { calls.incrementAndGet() }

        assertEquals(2, calls.get())
        assertTrue(v2 > v1)
    }

    @Test
    fun `get should refresh when expired`() = runBlocking {
        val calls = AtomicInteger(0)
        var now = 0L
        val cache = TimedSuspendCache<Int>(
            maxAgeMs = 100L,
            nowMs = { now },
        )

        cache.get { calls.incrementAndGet() }
        now = 101L
        cache.get { calls.incrementAndGet() }

        assertEquals(2, calls.get())
    }

    @Test
    fun `get should dedupe concurrent loads`() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TimedSuspendCache<Int>(
            maxAgeMs = 10_000L,
            nowMs = { 0L },
        )

        val results = (1..20)
            .map {
                async(Dispatchers.Default) {
                    cache.get {
                        delay(30)
                        calls.incrementAndGet()
                        42
                    }
                }
            }
            .awaitAll()

        assertEquals(1, calls.get())
        assertEquals(20, results.size)
        assertTrue(results.all { it == 42 })
    }
}
