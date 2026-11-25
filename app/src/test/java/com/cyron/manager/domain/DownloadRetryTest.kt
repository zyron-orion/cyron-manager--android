package com.cyron.manager.domain

import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.model.resetForRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRetryTest {
    @Test fun retryStartsFromAFreshProbeInsteadOfTheStaleValidators() {
        val original = DownloadTask(
            id = "retry", url = "https://example.test/latest.zip", fileName = "latest.zip",
            totalBytes = 5000, downloadedBytes = 1200, rangeSupported = true,
            status = DownloadStatus.FAILED, savedUri = "content://provider/tree/downloads",
            expectedSha256 = "a".repeat(64), etag = "\"old\"", lastModified = "Wed, 01 Jan 2025 00:00:00 GMT",
            error = "The remote file changed since the original request. Unsafe partial data was discarded."
        )

        val retried = original.resetForRetry(now = 99L)

        assertEquals(DownloadStatus.QUEUED, retried.status)
        assertEquals(-1L, retried.totalBytes)
        assertEquals(0L, retried.downloadedBytes)
        assertNull(retried.etag)
        assertNull(retried.lastModified)
        assertNull(retried.error)
        assertEquals(99L, retried.updatedAt)
    }

    @Test fun retryKeepsEverythingThatIdentifiesTheTransfer() {
        val original = DownloadTask(
            id = "retry", url = "https://example.test/latest.zip", fileName = "latest.zip",
            totalBytes = 5000, status = DownloadStatus.CANCELLED, connectionCount = 8,
            savedUri = "content://provider/tree/downloads", expectedSha256 = "b".repeat(64), etag = "\"old\""
        )

        val retried = original.resetForRetry(now = 100L)

        assertEquals(original.id, retried.id)
        assertEquals(original.url, retried.url)
        assertEquals(original.fileName, retried.fileName)
        assertEquals(original.savedUri, retried.savedUri)
        assertEquals(original.expectedSha256, retried.expectedSha256)
        assertEquals(original.connectionCount, retried.connectionCount)
        assertEquals(original.createdAt, retried.createdAt)
    }
}
