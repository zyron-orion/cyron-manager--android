package com.cyron.manager.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadManagerTest {
    @Test fun parsesJsonFormatsWithoutTreatingFormatIdsAsHeights() {
        val info = MediaFormatChoice.parseInfo(
            """{"title":"Example title","formats":[
                {"height":1080,"vcodec":"avc1.64002a","acodec":"none"},
                {"height":720,"vcodec":"vp9","acodec":"none"},
                {"height":0,"vcodec":"none","acodec":"opus"}
            ]}""".trimIndent(),
            "fallback"
        )

        assertEquals("Example title", info.title)
        assertEquals(listOf(1080, 720), info.availableHeights)
        assertTrue(info.audioOnlyAvailable)
    }

    @Test(expected = IllegalStateException::class)
    fun playlistsAreRejectedInsteadOfSilentlyTakingTheFirstEntry() {
        MediaFormatChoice.parseInfo("""{"_type":"playlist","title":"A playlist","entries":[]}""", "fallback")
    }

    @Test fun youtubeChoicesDefaultToMergedBestQuality() {
        val choices = MediaFormatChoice.choices(MediaInfo("Example", listOf(2160, 1080, 720), true), preferMerged = true)

        assertEquals("Best quality", choices.first().label)
        val parsed = MediaFormatChoice.fromSelector(choices.first().selector)
        assertEquals("bestvideo[height<=1080]", parsed.videoSelector)
        assertEquals("best[vcodec!=none][acodec!=none]", parsed.fallbackSelector)
    }

    @Test fun choicesUseDistinctAudioSelectorsAndConversionFlag() {
        val choices = MediaFormatChoice.choices(MediaInfo("Example", listOf(1080), true))

        assertEquals(listOf("Best single file", "1080p", "Audio only", "Audio · MP3"), choices.map { it.label })
        assertEquals("__audio_only__", choices.first { it.label == "Audio only" }.selector)
        assertEquals("__audio_mp3__", choices.first { it.label == "Audio · MP3" }.selector)
        assertFalse(choices.first { it.label == "Audio only" }.convertToMp3)
        assertTrue(choices.first { it.label == "Audio · MP3" }.convertToMp3)
    }

    @Test fun connectionCountsAreClampedToTheSupportedRange() {
        assertEquals(1, normalizeMediaConnections(Int.MIN_VALUE))
        assertEquals(8, normalizeMediaConnections(8))
        assertEquals(16, normalizeMediaConnections(Int.MAX_VALUE))
    }
}
