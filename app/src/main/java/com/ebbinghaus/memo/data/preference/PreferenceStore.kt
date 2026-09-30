package com.ebbinghaus.memo.data.preference

import android.content.Context
import android.content.SharedPreferences
import com.ebbinghaus.memo.data.repository.MemoSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量偏好存储（D7）
 *
 * 承载「排序选项 / 快照开关 / 快照目录 URI / 上次快照日 / 保留份数」等 UI 偏好与 SAF URI。
 * 刻意使用 `SharedPreferences`：零新依赖、零 Room 迁移；若改存 Room 会触发第二次迁移（违背唯一迁移项）。
 *
 * 抽象为接口以便纯 JVM 单测注入内存替身。
 */
interface PreferenceStore {

    /** 列表排序选项 */
    val sortOption: StateFlow<MemoSortOption>

    /** 自动快照开关 */
    val snapshotEnabled: StateFlow<Boolean>

    /** 快照目录 SAF URI（未选择时为 null） */
    val snapshotDirUri: StateFlow<String?>

    /** 上次成功快照的自然日（ISO yyyy-MM-dd，用于同日去重；未快照过为 null） */
    val lastSnapshotDate: StateFlow<String?>

    /** 快照保留份数 */
    val snapshotKeepCount: StateFlow<Int>

    fun setSortOption(option: MemoSortOption)

    fun setSnapshotEnabled(enabled: Boolean)

    fun setSnapshotDirUri(uri: String?)

    fun setLastSnapshotDate(date: String?)

    fun setSnapshotKeepCount(count: Int)
}

/**
 * `SharedPreferences` 实现：写入即持久化，读取走内存 `StateFlow`（同步初值 + 变更广播）。
 */
class SharedPreferencesPreferenceStore(context: Context) : PreferenceStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sortOption = MutableStateFlow(
        MemoSortOption.fromOrdinal(prefs.getInt(KEY_SORT_OPTION, MemoSortOption.DEFAULT.ordinal))
    )
    override val sortOption: StateFlow<MemoSortOption> = _sortOption.asStateFlow()

    private val _snapshotEnabled = MutableStateFlow(prefs.getBoolean(KEY_SNAPSHOT_ENABLED, false))
    override val snapshotEnabled: StateFlow<Boolean> = _snapshotEnabled.asStateFlow()

    private val _snapshotDirUri = MutableStateFlow(prefs.getString(KEY_SNAPSHOT_DIR_URI, null))
    override val snapshotDirUri: StateFlow<String?> = _snapshotDirUri.asStateFlow()

    private val _lastSnapshotDate = MutableStateFlow(prefs.getString(KEY_LAST_SNAPSHOT_DATE, null))
    override val lastSnapshotDate: StateFlow<String?> = _lastSnapshotDate.asStateFlow()

    private val _snapshotKeepCount = MutableStateFlow(
        prefs.getInt(KEY_SNAPSHOT_KEEP_COUNT, DEFAULT_KEEP_COUNT)
    )
    override val snapshotKeepCount: StateFlow<Int> = _snapshotKeepCount.asStateFlow()

    override fun setSortOption(option: MemoSortOption) {
        _sortOption.value = option
        prefs.edit().putInt(KEY_SORT_OPTION, option.ordinal).apply()
    }

    override fun setSnapshotEnabled(enabled: Boolean) {
        _snapshotEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SNAPSHOT_ENABLED, enabled).apply()
    }

    override fun setSnapshotDirUri(uri: String?) {
        _snapshotDirUri.value = uri
        prefs.edit().putString(KEY_SNAPSHOT_DIR_URI, uri).apply()
    }

    override fun setLastSnapshotDate(date: String?) {
        _lastSnapshotDate.value = date
        prefs.edit().putString(KEY_LAST_SNAPSHOT_DATE, date).apply()
    }

    override fun setSnapshotKeepCount(count: Int) {
        val clamped = count.coerceIn(MIN_KEEP_COUNT, MAX_KEEP_COUNT)
        _snapshotKeepCount.value = clamped
        prefs.edit().putInt(KEY_SNAPSHOT_KEEP_COUNT, clamped).apply()
    }

    companion object {
        /** 偏好文件名 */
        const val PREFS_NAME = "ebbinghaus_prefs"

        /** 默认快照保留份数 */
        const val DEFAULT_KEEP_COUNT = 7

        /** 保留份数下界 */
        const val MIN_KEEP_COUNT = 1

        /** 保留份数上界 */
        const val MAX_KEEP_COUNT = 30

        private const val KEY_SORT_OPTION = "sort_option"
        private const val KEY_SNAPSHOT_ENABLED = "snapshot_enabled"
        private const val KEY_SNAPSHOT_DIR_URI = "snapshot_dir_uri"
        private const val KEY_LAST_SNAPSHOT_DATE = "last_snapshot_date"
        private const val KEY_SNAPSHOT_KEEP_COUNT = "snapshot_keep_count"
    }
}
