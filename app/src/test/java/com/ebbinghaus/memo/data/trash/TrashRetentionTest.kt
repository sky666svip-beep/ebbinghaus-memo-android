package com.ebbinghaus.memo.data.trash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回收站保留策略纯函数单元测试（E06）
 *
 * 覆盖：30 天 cutoff、剩余天数边界、已删除天数取整。
 */
class TrashRetentionTest {

    private val day: Long = 24L * 60L * 60L * 1000L

    @Test
    fun retentionDays_isThirty() {
        assertEquals(30, TrashRetention.RETENTION_DAYS)
    }

    @Test
    fun purgeCutoff_isExactly30DaysBeforeNow() {
        val now = 1_700_000_000_000L
        assertEquals(now - 30 * day, TrashRetention.purgeCutoff(now))
    }

    @Test
    fun remainingDays_fullWindowOnFreshDelete() {
        val now = 1_700_000_000_000L
        assertEquals(30, TrashRetention.remainingDays(now, now))
    }

    @Test
    fun remainingDays_decreasesWithElapsed() {
        val now = 1_700_000_000_000L
        val deletedAt = now - 10 * day
        assertEquals(20, TrashRetention.remainingDays(deletedAt, now))
    }

    @Test
    fun remainingDays_atAndBeyondRetentionBoundary() {
        val now = 1_700_000_000_000L
        // 边界日三连（反向合并自 remainingDays_atDay29_30_31，C 档 2026-09-18）：
        // 第 29 天与第 31 天原仅存于弱版，合并后不得丢失
        assertEquals("第 29 天：剩余 1 天", 1, TrashRetention.remainingDays(now - 29 * day, now))
        // 恰好 30 天：剩余 0
        assertEquals(0, TrashRetention.remainingDays(now - 30 * day, now))
        // 第 31 天：仍为 0，不出现负数
        assertEquals("第 31 天：剩余 0 天（不为负）", 0, TrashRetention.remainingDays(now - 31 * day, now))
        // 超过 30 天：仍为 0，不出现负数
        assertEquals(0, TrashRetention.remainingDays(now - 45 * day, now))
    }

    @Test
    fun remainingDays_roundsUpPartialDay() {
        val now = 1_700_000_000_000L
        // 已过 29.5 天 → 剩余 0.5 天，向上取整为 1
        val deletedAt = now - (29 * day + day / 2)
        assertEquals(1, TrashRetention.remainingDays(deletedAt, now))
    }

    @Test
    fun elapsedDays_floorsAndClampsNegative() {
        val now = 1_700_000_000_000L
        assertEquals(0, TrashRetention.elapsedDays(now, now))
        assertEquals(0, TrashRetention.elapsedDays(now + day, now))
        assertEquals(2, TrashRetention.elapsedDays(now - (2 * day + day / 2), now))
    }
}
