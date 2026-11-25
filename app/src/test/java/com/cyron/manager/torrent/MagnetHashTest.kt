package com.cyron.manager.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetHashTest {
    @Test fun parsesHexAndBase32BitTorrentHashes() {
        val hex = "0123456789abcdef0123456789abcdef01234567"
        val expected = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(expected, TorrentDownloadManager.parseMagnetHash("magnet:?xt=urn:btih:$hex"))
        assertEquals(expected, TorrentDownloadManager.parseMagnetHash("magnet:?xt=urn:btih:AERUKZ4JVPG66AJDIVTYTK6N54ASGRLH"))
    }

    @Test fun rejectsMalformedHashes() {
        assertTrue(TorrentDownloadManager.parseMagnetHash("magnet:?xt=urn:btih:invalid").isEmpty())
        assertTrue(TorrentDownloadManager.parseMagnetHash("https://example.test/file.torrent").isEmpty())
    }
}
