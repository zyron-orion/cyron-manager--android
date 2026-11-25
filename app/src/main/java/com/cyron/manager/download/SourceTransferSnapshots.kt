package com.cyron.manager.download

import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.TransferSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SourceTransferSnapshots @Inject constructor() {
    private val state = MutableStateFlow<Map<String, TransferSnapshot>>(emptyMap())
    fun snapshots() = state.asStateFlow()
    fun publish(snapshot: TransferSnapshot) { state.value = state.value + (snapshot.downloadId to snapshot) }
    fun remove(id: String) { state.value = state.value - id }
    fun reset(id: String, totalBytes: Long = -1) = publish(TransferSnapshot(id, 0, totalBytes, 0, DownloadStatus.DOWNLOADING))
}
