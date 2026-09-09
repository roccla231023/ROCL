package me.rerere.rikkahub.data.datastore

import java.math.BigDecimal

const val DEFAULT_EMBEDDING_RETRIEVAL_TIMEOUT_MILLIS = 2_000L

fun formatEmbeddingRetrievalTimeoutSeconds(timeoutMillis: Long): String {
    return BigDecimal.valueOf(timeoutMillis, 3).stripTrailingZeros().toPlainString()
}

fun parseEmbeddingRetrievalTimeoutMillis(raw: String): Long? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val seconds = value.toBigDecimalOrNull() ?: return null
    if (seconds.signum() < 0) return null
    return seconds.movePointRight(3).toLong()
}

fun Settings.getEmbeddingRetrievalTimeoutMillis(): Long {
    val timeout = displaySetting.embeddingRetrievalTimeoutMillis
    return if (timeout > 0L) timeout else DEFAULT_EMBEDDING_RETRIEVAL_TIMEOUT_MILLIS
}
