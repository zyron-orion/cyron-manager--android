package com.cyron.manager.download

import android.net.Uri
import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.domain.model.ProbeResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class ProbedDownload(val result: ProbeResult, val fileName: String, val mimeType: String)

class ProbeFileUseCase @Inject constructor(private val engine: DownloadEngine) {
    suspend operator fun invoke(url: String): ProbedDownload {
        val result = engine.probe(url.trim())
        val filename = result.suggestedName?.takeIf { it.isNotBlank() }?.let(DownloadName::sanitize)
            ?: runCatching {
                result.effectiveUrl.toHttpUrlOrNull()?.pathSegments?.lastOrNull()?.takeIf(String::isNotBlank)
            }.getOrNull() ?: Uri.parse(result.effectiveUrl).host?.takeIf(String::isNotBlank) ?: "download.bin"
        return ProbedDownload(result, DownloadName.sanitize(filename), result.mimeType.ifBlank { "application/octet-stream" })
    }
}
