package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightlyUpdateTest {

    @Test
    fun `commit sha is parsed from CI body`() {
        val body = """
            自动每日构建（commit `d971d303c0af4eaf16d9eb9a1d31accbbb944737`）

            > ⚠️ 开发版本，可能不稳定，仅供测试。
        """.trimIndent()
        assertEquals(
            "d971d303c0af4eaf16d9eb9a1d31accbbb944737",
            commitShaFromReleaseBody(body),
        )
    }

    @Test
    fun `missing commit is null`() {
        assertEquals(null, commitShaFromReleaseBody("no marker here"))
        assertEquals(null, commitShaFromReleaseBody(null))
    }

    @Test
    fun `same stable version is not offered`() {
        assertFalse(shouldOfferStableUpdate("1.0", "1.0"))
        assertFalse(shouldOfferStableUpdate("1.0", "v1.0"))
        assertFalse(shouldOfferStableUpdate("1.1", "1.0"))
    }

    @Test
    fun `newer stable version is offered`() {
        assertTrue(shouldOfferStableUpdate("1.0", "1.1"))
        assertTrue(shouldOfferStableUpdate("1.0", "v1.1"))
    }

    @Test
    fun `blank remote is not offered`() {
        assertFalse(shouldOfferStableUpdate("1.0", null))
        assertFalse(shouldOfferStableUpdate("1.0", ""))
    }

    @Test
    fun `asset size formatted in megabytes`() {
        assertEquals("1.0 MB", formatAssetSize(1_048_576))
        assertEquals("", formatAssetSize(0))
    }

    @Test
    fun `arm64 apk is preferred`() {
        val picked = pickNightlyApk(
            listOf(
                UpdateDownload("app-x86_64-release.apk", "u1", "1"),
                UpdateDownload("app-arm64-v8a-release.apk", "u2", "1"),
                UpdateDownload("mapping.txt", "u3", "1"),
            )
        )
        assertEquals(listOf("app-arm64-v8a-release.apk"), picked.map { it.name })
    }
}
