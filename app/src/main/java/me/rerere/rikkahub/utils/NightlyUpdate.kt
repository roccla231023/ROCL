package me.rerere.rikkahub.utils

import java.util.Locale

const val ROCL_GITHUB_URL = "https://github.com/roccla231023/ROCL"
const val ROCL_NIGHTLY_API_URL =
    "https://api.github.com/repos/roccla231023/ROCL/releases/tags/nightly"

private val COMMIT_IN_BODY = Regex("""commit [`']([0-9a-fA-F]{7,40})[`']""")

fun commitShaFromReleaseBody(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return COMMIT_IN_BODY.find(body)?.groupValues?.get(1)
}

fun shouldOfferNightlyUpdate(localSha: String, remoteSha: String?): Boolean {
    val local = localSha.trim()
    val remote = remoteSha?.trim().orEmpty()
    if (remote.length < 7 || local.length < 7) return false
    if (local.equals("unknown", ignoreCase = true)) return false
    val a = local.lowercase(Locale.US)
    val b = remote.lowercase(Locale.US)
    if (a.startsWith(b) || b.startsWith(a)) return false
    return true
}

fun formatAssetSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    return String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
}

fun pickNightlyApk(assets: List<UpdateDownload>): List<UpdateDownload> {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    val arm = apks.filter { it.name.contains("arm64", ignoreCase = true) }
    if (arm.isNotEmpty()) return arm
    val universal = apks.filter { it.name.contains("universal", ignoreCase = true) }
    if (universal.isNotEmpty()) return universal
    return apks
}
