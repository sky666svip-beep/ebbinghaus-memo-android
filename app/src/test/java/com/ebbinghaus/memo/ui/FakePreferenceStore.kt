package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.preference.PreferenceStore
import com.ebbinghaus.memo.data.repository.MemoSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 纯内存偏好存储替身，用于纯 JVM 单元测试。
 *
 * 模拟「重启后保持」：同一实例在测试内被重新注入新建的 ViewModel 时，读取到的是先前写入的值。
 */
class FakePreferenceStore(
    initialSort: MemoSortOption = MemoSortOption.DEFAULT,
    initialSnapshotEnabled: Boolean = false,
    initialSnapshotDirUri: String? = null,
    initialLastSnapshotDate: String? = null,
    initialKeepCount: Int = 7
) : PreferenceStore {

    private val _sortOption = MutableStateFlow(initialSort)
    override val sortOption: StateFlow<MemoSortOption> = _sortOption.asStateFlow()

    private val _snapshotEnabled = MutableStateFlow(initialSnapshotEnabled)
    override val snapshotEnabled: StateFlow<Boolean> = _snapshotEnabled.asStateFlow()

    private val _snapshotDirUri = MutableStateFlow(initialSnapshotDirUri)
    override val snapshotDirUri: StateFlow<String?> = _snapshotDirUri.asStateFlow()

    private val _lastSnapshotDate = MutableStateFlow(initialLastSnapshotDate)
    override val lastSnapshotDate: StateFlow<String?> = _lastSnapshotDate.asStateFlow()

    private val _snapshotKeepCount = MutableStateFlow(initialKeepCount)
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
