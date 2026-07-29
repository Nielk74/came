package com.nielk74.came.camera

import java.net.URI

/**
 * Accepts only absolute web links from QR payloads.
 *
 * QR codes can carry arbitrary text and custom URI schemes. camé presents a browser link, so plain
 * text and app-deep-link payloads deliberately remain invisible.
 */
internal fun normalizeQrWebLink(rawValue: String?): String? {
    val candidate = rawValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in WEB_SCHEMES || uri.rawAuthority.isNullOrBlank()) return null
    return candidate
}

internal fun firstQrWebLink(rawValues: Iterable<String?>): String? =
    rawValues.firstNotNullOfOrNull(::normalizeQrWebLink)

/**
 * Holds a recognized link through brief missed frames so the chip remains readable and tappable.
 */
internal class QrLinkTracker(
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
) {
    private var currentLink: String? = null
    private var lastSeenAtMillis = Long.MIN_VALUE

    init {
        require(retentionMillis >= 0)
    }

    fun update(detectedLink: String?, nowMillis: Long): String? {
        if (detectedLink != null) {
            currentLink = detectedLink
            lastSeenAtMillis = nowMillis
        } else if (
            currentLink != null &&
            elapsedSinceLastSeen(nowMillis) >= retentionMillis
        ) {
            clear()
        }
        return currentLink
    }

    fun clear() {
        currentLink = null
        lastSeenAtMillis = Long.MIN_VALUE
    }

    private fun elapsedSinceLastSeen(nowMillis: Long): Long =
        if (nowMillis < lastSeenAtMillis) Long.MAX_VALUE else nowMillis - lastSeenAtMillis

    private companion object {
        const val DEFAULT_RETENTION_MILLIS = 1_200L
    }
}

private val WEB_SCHEMES = setOf("http", "https")
