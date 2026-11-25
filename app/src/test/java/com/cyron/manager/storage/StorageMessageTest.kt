package com.cyron.manager.storage

import com.cyron.manager.presentation.localizeUiText
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMessageTest {
    @Test fun everyStorageMessageTheUserCanSeeHasPersianCopy() {
        val messages = StorageMessage::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }

        assertTrue("expected the storage message catalogue, found ${messages.size}", messages.size >= 18)
        messages.forEach { message ->
            assertEquals(message, localizeUiText(message, "en"))
            assertNotEquals("Missing Persian copy for: $message", message, localizeUiText(message, "fa"))
        }
    }
}
