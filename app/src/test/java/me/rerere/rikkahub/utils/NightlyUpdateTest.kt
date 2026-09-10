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
    fun `same nightly is not offered`() {
        val sha = "d971d303c0af4eaf16d9eb9a1d31accbbb944737"
        assertFalse(shouldOfferNightlyUpdate(sha, sha))
        assertFalse(shouldOfferNightlyUpdate(sha, sha.take(7)))
        assertFalse(shouldOfferNightlyUpdate(sha.take(7), sha))
    }

    @Test
    fun `different sha is offered`() {
        assertTrue(
            shouldOfferNightlyUpdate(
                "d971d303c0af4eaf16d9eb9a1d31accbbb944737",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            )
        )
    }

    @Test
    fun `unknown or short sha is not offered`() {
        assertFalse(shouldOfferNightlyUpdate("unknown", "d971d30"))
        assertFalse(shouldOfferNightlyUpdate("abc", "d971d30"))
        assertFalse(shouldOfferNightlyUpdate("d971d30", null))
        assertFalse(shouldOfferNightlyUpdate("d971d30", ""))
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
