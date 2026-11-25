package com.cyron.manager.media

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStallWatchdogTest {
    @Test fun aQuietTransferIsStalledOnlyAfterTheTimeout() {
        val watchdog = MediaStallWatchdog(timeoutMillis = 1_000, startedAt = 0)
        assertFalse(watchdog.stalled(now = 999))
        assertFalse(watchdog.stalled(now = 1_000))
        assertTrue(watchdog.stalled(now = 1_001))
    }

    @Test fun anyNewActivityRestartsTheWindow() {
        val watchdog = MediaStallWatchdog(timeoutMillis = 1_000, startedAt = 0)
        watchdog.onActivity(now = 2_500)
        assertFalse(watchdog.stalled(now = 3_400))
        assertTrue(watchdog.stalled(now = 3_501))
    }

    @Test fun anOutOfOrderCallbackCannotShortenTheWindow() {
        val watchdog = MediaStallWatchdog(timeoutMillis = 1_000, startedAt = 0)
        watchdog.onActivity(now = 5_000)
        watchdog.onActivity(now = 1_000)
        assertFalse(watchdog.stalled(now = 5_999))
    }

    @Test fun aSlowDownloadThatKeepsProducingOutputIsNeverCutOff() {
        val watchdog = MediaStallWatchdog(timeoutMillis = 3 * 60 * 1000L, startedAt = 0)
        // Ten minutes of steady progress: more than twice the fixed deadline that used to kill the runner path.
        repeat(600) { second ->
            val now = (second + 1) * 1_000L
            if (second % 5 == 0) watchdog.onActivity(now)
            assertFalse("still active at $now", watchdog.stalled(now))
        }
    }

    @Test fun stagedBytesCountsOnlyTheFilesOfOneOutputPrefix() {
        val directory = Files.createTempDirectory("cyron-stall").toFile()
        try {
            directory.resolve("media-x-video.mp4.part").writeBytes(ByteArray(10))
            directory.resolve("media-x-video.mp4.ytdl").writeText("state")
            directory.resolve("media-y-audio.m4a").writeBytes(ByteArray(50))
            directory.resolve("unrelated.bin").writeBytes(ByteArray(100))

            assertEquals(15L, stagedBytes(directory, "media-x-video"))
            assertEquals(50L, stagedBytes(directory, "media-y-audio"))
            assertEquals(0L, stagedBytes(directory, "missing"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
