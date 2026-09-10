package me.rerere.rikkahub.utils

import java.util.Locale

const val ROCL_GITHUB_URL = "https://github.com/roccla231023/ROCL"
const val ROCL_LATEST_API_URL =
    "https://api.github.com/repos/roccla231023/ROCL/releases/latest"

private val COMMIT_IN_BODY = Regex("""commit [`']([0-9a-fA-F]{7,40})[`']""")

fun commitShaFromReleaseBody(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return COMMIT_IN_BODY.find(body)?.groupValues?.get(1)
}

fun versionFromReleaseTag(tag: String?): String {
    return tag.orEmpty().trim().removePrefix("v").removePrefix("V")
}

fun shouldOfferStableUpdate(localVersion: String, remoteVersion: String?): Boolean {
    val local = versionFromReleaseTag(localVersion)
    val remote = versionFromReleaseTag(remoteVersion)
    if (local.isEmpty() || remote.isEmpty()) return false
    return Version(remote) > Version(local)
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
