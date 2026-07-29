package com.nielk74.came.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLinkTest {
    @Test
    fun acceptsOnlyAbsoluteHttpAndHttpsLinks() {
        assertEquals(
            "https://example.com/a?b=1#result",
            normalizeQrWebLink("  https://example.com/a?b=1#result  "),
        )
        assertEquals("HTTP://example.com", normalizeQrWebLink("HTTP://example.com"))

        assertNull(normalizeQrWebLink("A QR code with plain text"))
        assertNull(normalizeQrWebLink("www.example.com"))
        assertNull(normalizeQrWebLink("/relative"))
        assertNull(normalizeQrWebLink("mailto:hello@example.com"))
        assertNull(normalizeQrWebLink("javascript:alert(1)"))
        assertNull(normalizeQrWebLink("https:///missing-host"))
    }

    @Test
    fun firstValidWebLinkWinsWhenAFrameContainsSeveralPayloads() {
        assertEquals(
            "https://first.example",
            firstQrWebLink(
                listOf(
                    "plain text",
                    "https://first.example",
                    "https://second.example",
                ),
            ),
        )
        assertNull(firstQrWebLink(listOf(null, "wifi:S:network;T:WPA;P:secret;;")))
    }

    @Test
    fun trackerBridgesBriefMissesThenClearsTheLink() {
        val tracker = QrLinkTracker(retentionMillis = 1_200)

        assertEquals("https://example.com", tracker.update("https://example.com", nowMillis = 100))
        assertEquals("https://example.com", tracker.update(null, nowMillis = 1_299))
        assertNull(tracker.update(null, nowMillis = 1_300))
    }

    @Test
    fun trackerReplacesARecognizedLinkAndCanBeResetImmediately() {
        val tracker = QrLinkTracker()

        tracker.update("https://first.example", nowMillis = 1)
        assertEquals(
            "https://second.example",
            tracker.update("https://second.example", nowMillis = 2),
        )
        tracker.clear()
        assertNull(tracker.update(null, nowMillis = 2))
    }
}
