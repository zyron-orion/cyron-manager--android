package com.cyron.manager.domain

import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.model.TransferSnapshot
import com.cyron.manager.domain.model.visibleProgress
import com.cyron.manager.domain.model.visibleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleStatusTest {
    private fun task(status: DownloadStatus, bytes: Long = 25, total: Long = 100) =
        DownloadTask(url = "https://example.com/a", fileName = "a.bin", status = status, downloadedBytes = bytes, totalBytes = total)

    private fun snapshot(status: DownloadStatus, bytes: Long = 90, total: Long = 100, fraction: Float? = null) =
        TransferSnapshot("x", bytes, total, 0, status, fraction = fraction)

    @Test fun queuedTaskDoesNotAdoptAStaleRunningSnapshot() {
        assertEquals(DownloadStatus.QUEUED, task(DownloadStatus.QUEUED).visibleStatus(snapshot(DownloadStatus.DOWNLOADING)))
        assertEquals(0.25f, task(DownloadStatus.QUEUED).visibleProgress(snapshot(DownloadStatus.DOWNLOADING)), 0.001f)
    }

    @Test fun completedTaskDoesNotGoBackToDownloading() {
        assertEquals(DownloadStatus.COMPLETED, task(DownloadStatus.COMPLETED, 100).visibleStatus(snapshot(DownloadStatus.DOWNLOADING)))
        assertEquals(1f, task(DownloadStatus.COMPLETED, 100).visibleProgress(snapshot(DownloadStatus.DOWNLOADING)), 0.001f)
    }

    @Test fun runningTaskUsesLiveSnapshot() {
        val live = task(DownloadStatus.DOWNLOADING, 10)
        assertEquals(DownloadStatus.FAILED, live.visibleStatus(snapshot(DownloadStatus.FAILED)))
        assertEquals(0.9f, live.visibleProgress(snapshot(DownloadStatus.DOWNLOADING)), 0.001f)
    }

    @Test fun mediaFractionIsUsedForProgress() {
        val live = task(DownloadStatus.DOWNLOADING, 0, -1)
        assertEquals(0.42f, live.visibleProgress(snapshot(DownloadStatus.DOWNLOADING, 0, -1, 0.42f)), 0.001f)
    }

    @Test fun publishingUsesItsLiveSnapshotAndNeverReopensCompletedWork() {
        val publishing = task(DownloadStatus.PUBLISHING, 100)
        assertEquals(DownloadStatus.PUBLISHING, publishing.visibleStatus(snapshot(DownloadStatus.PUBLISHING, 100)))
        assertEquals(1f, publishing.visibleProgress(snapshot(DownloadStatus.PUBLISHING, 100)), 0.001f)
        assertEquals(DownloadStatus.COMPLETED, task(DownloadStatus.COMPLETED, 100).visibleStatus(snapshot(DownloadStatus.PUBLISHING, 100)))
    }
}
