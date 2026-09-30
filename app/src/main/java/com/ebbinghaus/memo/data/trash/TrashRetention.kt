package com.ebbinghaus.memo.data.trash

/**
 * 回收站保留策略（E06）
 *
 * 纯函数，零依赖，便于纯 JVM 单元测试。保留天数对齐产品口径固定为 30 天，不做可配。
 */
object TrashRetention {

    /** 回收站条目保留天数 */
    const val RETENTION_DAYS: Int = 30

    /** 一天的毫秒数 */
    private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

    /**
     * 计算物理清除的截止时间戳。
     *
     * `deletedAt < cutoff` 的条目将被物理删除（连带 CASCADE 清除 review_tasks）。
     *
     * @param nowMillis 当前时刻（epoch 毫秒）
     */
    fun purgeCutoff(nowMillis: Long): Long = nowMillis - RETENTION_DAYS * MILLIS_PER_DAY

    /**
     * 计算回收站条目剩余保留天数（向上取整，钳制在 0~[RETENTION_DAYS]）。
     *
     * @param deletedAtMillis 条目删除时刻（epoch 毫秒）
     * @param nowMillis 当前时刻（epoch 毫秒）
     */
    fun remainingDays(deletedAtMillis: Long, nowMillis: Long): Int {
        val remainMillis = RETENTION_DAYS * MILLIS_PER_DAY - (nowMillis - deletedAtMillis)
        if (remainMillis <= 0L) return 0
        val days = (remainMillis + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY
        return days.toInt().coerceIn(0, RETENTION_DAYS)
    }

    /**
     * 计算条目已删除的天数（向下取整，最小 0）。
     *
     * @param deletedAtMillis 条目删除时刻（epoch 毫秒）
     * @param nowMillis 当前时刻（epoch 毫秒）
     */
    fun elapsedDays(deletedAtMillis: Long, nowMillis: Long): Int {
        val elapsed = nowMillis - deletedAtMillis
        if (elapsed <= 0L) return 0
        return (elapsed / MILLIS_PER_DAY).toInt()
    }
}
