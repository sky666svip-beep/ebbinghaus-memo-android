package com.ebbinghaus.memo.ui.preview

import com.ebbinghaus.memo.data.preference.PreferenceStore
import com.ebbinghaus.memo.data.repository.MemoSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preview 专用内存偏好存储（仅 debug 变体，不进 release）。
 */
internal class PreviewPreferenceStore(
    initialSort: MemoSortOption = MemoSortOption.DEFAULT
) : PreferenceStore {

    private val _sortOption = MutableStateFlow(initialSort)
    override val sortOption: StateFlow<MemoSortOption> = _sortOption.asStateFlow()

    private val _snapshotEnabled = MutableStateFlow(false)
    override val snapshotEnabled: StateFlow<Boolean> = _snapshotEnabled.asStateFlow()

    private val _snapshotDirUri = MutableStateFlow<String?>(null)
    override val snapshotDirUri: StateFlow<String?> = _snapshotDirUri.asStateFlow()

    private val _lastSnapshotDate = MutableStateFlow<String?>(null)
    override val lastSnapshotDate: StateFlow<String?> = _lastSnapshotDate.asStateFlow()

    private val _snapshotKeepCount = MutableStateFlow(7)
    override val snapshotKeepCount: StateFlow<Int> = _snapshotKeepCount.asStateFlow()

    override fun setSortOption(option: MemoSortOption) {
        _sortOption.value = option
    }

    override fun setSnapshotEnabled(enabled: Boolean) {
        _snapshotEnabled.value = enabled
    }

    override fun setSnapshotDirUri(uri: String?) {
        _snapshotDirUri.value = uri
    }

    override fun setLastSnapshotDate(date: String?) {
        _lastSnapshotDate.value = date
    }

    override fun setSnapshotKeepCount(count: Int) {
        _snapshotKeepCount.value = count
    }
}
