package com.cyron.manager.background

import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.TransferSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTickTest {
    private val interval = DownloadForegroundService.PROGRESS_PERSIST_INTERVAL_MS

    @Test fun torrentProgressIsPersistedWithoutAnyActiveHttpTransfer() {
        val last = mutableMapOf<String, Long>()
        val torrent = TransferSnapshot("torrent-1", downloadedBytes = 500, totalBytes = 1000, bytesPerSecond = 0, status = DownloadStatus.DOWNLOADING)

        val tick = progressTick(listOf(torrent), last, now = 10_000)

        assertEquals(listOf(torrent), tick.toPersist)
        assertTrue(tick.shouldRender)
        assertEquals(10_000L, last["torrent-1"])
    }

    @Test fun progressIsWrittenAtMostOncePerInterval() {
        val last = mutableMapOf("job" to 10_000L)
        val snapshot = TransferSnapshot("job", downloadedBytes = 100, totalBytes = 1000, bytesPerSecond = 0, status = DownloadStatus.DOWNLOADING)

        val tooSoon = progressTick(listOf(snapshot), last, now = 10_000 + interval - 1)
        assertTrue(tooSoon.toPersist.isEmpty())
        assertFalse(tooSoon.shouldRender)

        val due = progressTick(listOf(snapshot), last, now = 10_000 + interval)
        assertEquals(listOf(snapshot), due.toPersist)
        assertTrue(due.shouldRender)
    }

    @Test fun mediaPercentagesRefreshTheNotificationButAreNotStoredAsBytes() {
        val media = TransferSnapshot("media", downloadedBytes = 0, totalBytes = -1, bytesPerSecond = 0, status = DownloadStatus.DOWNLOADING, fraction = 0.4f)

        val tick = progressTick(listOf(media), mutableMapOf(), now = 5_000)

        assertTrue(tick.toPersist.isEmpty())
        assertTrue(tick.shouldRender)
    }

    @Test fun finishedTransfersKeepTheRowTheCompletionWorkerWrote() {
        for (status in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) {
            val finished = TransferSnapshot("done", downloadedBytes = 1000, totalBytes = 1000, bytesPerSecond = 0, status = status)
            val tick = progressTick(listOf(finished), mutableMapOf(), now = 9_000)
            assertTrue(tick.toPersist.isEmpty())
            assertFalse(tick.shouldRender)
        }
    }
}
