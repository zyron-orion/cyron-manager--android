package com.cyron.manager.download

import com.cyron.manager.domain.model.DownloadFailure
import com.cyron.manager.storage.LocalStorageException
import com.cyron.manager.storage.hasEnoughStorage
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFailureTest {
    @Test fun localStorageFailuresAreNeverRetried() {
        assertFalse(isRetryableTransferFailure(LocalStorageException("write failed: ENOSPC (No space left on device)")))
    }

    @Test fun networkAndTransientServerErrorsStayRetryable() {
        assertTrue(isRetryableTransferFailure(IOException("connection reset")))
        assertTrue(isRetryableTransferFailure(EngineException(DownloadFailure.NETWORK, "timeout", true)))
        assertTrue(isRetryableTransferFailure(EngineException(DownloadFailure.HTTP_ERROR, "Server returned HTTP 503.", true)))
    }

    @Test fun changedResourcesAndPermanentErrorsStopTheRetryLoop() {
        assertFalse(isRetryableTransferFailure(EngineException(DownloadFailure.RESOURCE_CHANGED, "changed", true)))
        assertFalse(isRetryableTransferFailure(EngineException(DownloadFailure.HTTP_ERROR, "Server returned HTTP 404.", false)))
    }

    @Test fun aFullDiskFailsTheSpaceCheckWhileAnUnknownValueSkipsIt() {
        assertFalse(hasEnoughStorage(usableBytes = 0L, requiredBytes = 1L))
        assertTrue(hasEnoughStorage(usableBytes = -1L, requiredBytes = 1L))
        assertTrue(hasEnoughStorage(usableBytes = 100L, requiredBytes = 100L))
        assertFalse(hasEnoughStorage(usableBytes = 99L, requiredBytes = 100L))
    }
}
