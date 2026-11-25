package com.cyron.manager.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun translatesStaticLabelsAndPreservesDynamicValues() {
        assertEquals("تنظیمات", localizeUiText("Settings", "fa"))
        assertEquals("file.zip", localizeUiText("file.zip", "fa"))
        assertEquals("Settings", localizeUiText("Settings", "en"))
        assertEquals("بررسی لینک", localizeUiText("Inspect link", "fa"))
        assertEquals("4 اتصال HTTP (نیازمند Range و اعتبارسنج سرور)", localizeUiText("HTTP connections · 4 (requires server Range + validator)", "fa"))
        assertEquals("شروع دانلود", localizeUiText("  Start download", "fa").trim())
        assertEquals("دانلودها از اینترنت معمولی گوشی استفاده می‌کنند؛ Cyron به VPN نیاز ندارد. اگر سایتی فقط با VPN باز می‌شود، محدودیت از شبکه یا خود سایت است.", localizeUiText("Downloads use your device's normal network; Cyron does not require a VPN. If a site only works through a VPN, the restriction may be from the network or the source.", "fa"))
        assertEquals("1080p", localizeUiText("1080p", "fa"))
        // Numbers and counts stay Latin in the Persian UI.
        assertEquals("7", localizeUiText("7", "fa"))
        assertEquals("64%", localizeUiText("64%", "fa"))
    }

    @Test
    fun foldsPersianDigitsFromSystemFormatsBackToLatin() {
        assertEquals("24 نوامبر 2026", latinDigits("۲۴ نوامبر ۲۰۲۶"))
        assertEquals("1.5 GB", latinDigits("1.5 GB"))
    }

    @Test
    fun preservesWhitespaceAndTranslatesStatusLabels() {
        assertEquals("  ادامه  ", localizeUiText("  Resume  ", "fa"))
        assertEquals("در حال دانلود", localizeUiText("DOWNLOADING", "fa"))
    }

    @Test fun normalizesPersianAndArabicSearchVariants() {
        assertEquals("یاری کیبورد", normalizeSearchText("یاری کیبورد"))
        assertEquals("یاری کیبورد", normalizeSearchText("ياري كيبورد"))
        assertEquals("نام فایل", normalizeSearchText("نام\u200cفایل"))
        assertEquals("دانلودها", normalizeSearchText("  دانلودها  "))
    }

    @Test fun extractsHttpAndMagnetLinksFromSharedText() {
        val links = extractDownloadLinks("این را ببین: https://example.com/a.zip\nو magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        assertEquals(listOf("https://example.com/a.zip", "magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"), links)
    }

    @Test fun translatesNewQueueAndBatchMessages() {
        assertEquals("3 دانلود اضافه شد.", localizeUiText("Added 3 downloads.", "fa"))
        assertEquals("افزودن 2 لینک", localizeUiText("Add 2 links", "fa"))
        assertEquals("توقف همه", localizeUiText("Pause all", "fa"))
    }
}
