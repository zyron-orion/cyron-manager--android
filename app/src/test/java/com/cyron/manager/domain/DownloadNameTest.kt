package com.cyron.manager.domain

import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNameTest {
    @Test fun filenameCannotEscapeDestinationFolder() {
        assertEquals("secret.txt", DownloadName.sanitize("../../private/secret.txt"))
        assertEquals("secret.txt", DownloadName.sanitize("C:\\private\\secret.txt"))
        assertEquals("_CON.txt", DownloadName.sanitize("CON.txt"))
        assertTrue(DownloadName.sanitize("  ").isNotBlank())
        assertTrue(DownloadName.sanitize("safe.zip").length <= 180)
    }

    @Test fun aDownloadCannotHaveMoreThanSixteenConnections() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadTask(url = "https://example.test/file.bin", fileName = "file.bin", connectionCount = 17)
        }
    }

    @Test fun progressIsBoundedAndSizeAware() {
        val task = DownloadTask(url = "https://example.test/file.bin", fileName = "file.bin", totalBytes = 100, downloadedBytes = 200, status = DownloadStatus.DOWNLOADING)
        assertEquals(1f, task.progress)
        val unknown = DownloadTask(url = "https://example.test/stream", fileName = "stream")
        assertEquals(0f, unknown.progress)
    }
}
