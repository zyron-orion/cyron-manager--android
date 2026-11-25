package com.cyron.manager.download

import com.cyron.manager.domain.model.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IfRangeTest {
    @Test fun weakEtagIsNeverSentInIfRange() {
        assertNull(strongEtagForIfRange("W/\"abc\""))
        assertNull(strongEtagForIfRange(" w/\"abc\""))
    }

    @Test fun strongEtagAndLastModifiedRemainUsable() {
        assertEquals("\"abc\"", strongEtagForIfRange("\"abc\""))
        assertNull(strongEtagForIfRange(null))
    }

    @Test fun weakEtagAloneCannotAuthorizeResume() {
        val weak = ProbeResult("https://example.test/a", 100, true, "W/\"abc\"", null, "application/octet-stream")
        val strong = weak.copy(etag = "\"abc\"")
        val lastModified = weak.copy(lastModified = "Wed, 01 Jan 2025 00:00:00 GMT")
        assertFalse(weak.hasStrongValidator)
        assertTrue(strong.hasStrongValidator)
        assertTrue(lastModified.hasStrongValidator)
    }
}
