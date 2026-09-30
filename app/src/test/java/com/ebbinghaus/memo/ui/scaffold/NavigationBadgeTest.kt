package com.ebbinghaus.memo.ui.scaffold

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 复习 Tab 角标格式化单元测试
 *
 * 角标仅在 `badgeCount > 0` 时渲染，本套件保证文案不会溢出 Badge 容器。
 */
class NavigationBadgeTest {

    @Test
    fun formatBadgeCount_rendersPlainNumberUnderLimit() {
        assertEquals("1", formatBadgeCount(1))
        assertEquals("9", formatBadgeCount(9))
        assertEquals("42", formatBadgeCount(42))
        assertEquals("99", formatBadgeCount(99))
    }

    @Test
    fun formatBadgeCount_clampsOverflowTo99Plus() {
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("99+", formatBadgeCount(5000))
    }
}
