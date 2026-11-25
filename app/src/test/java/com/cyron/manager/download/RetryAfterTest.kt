package com.cyron.manager.download

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryAfterTest {
    @Test fun retryAfterSecondsAreConvertedAndCapped() {
        assertEquals(12_000L, parseRetryAfterMillis("12"))
        assertEquals(0L, parseRetryAfterMillis("-4"))
        assertEquals(86_400_000L, parseRetryAfterMillis("999999"))
    }

    @Test fun retryAfterHttpDatesAreSupported() {
        val future = Instant.now().plusSeconds(120).atOffset(ZoneOffset.UTC)
        val rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME.format(future)
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(future)
        assertTrue(parseRetryAfterMillis(rfc1123)!! in 118_000L..120_000L)
        assertTrue(parseRetryAfterMillis(iso)!! in 118_000L..120_000L)
    }

    @Test fun invalidRetryAfterValuesAreIgnored() {
        assertNull(parseRetryAfterMillis(null))
        assertNull(parseRetryAfterMillis(" "))
        assertNull(parseRetryAfterMillis("later"))
    }
}
