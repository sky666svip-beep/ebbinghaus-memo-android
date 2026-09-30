package com.ebbinghaus.memo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 窗口尺寸断点映射单元测试
 *
 * 覆盖 Material 3 官方阈值的临界值，防止断点判定在响应式改造中发生偏移。
 */
class WindowSizeClassTest {

    @Test
    fun widthSizeClass_mapsBoundaryValuesCorrectly() {
        assertEquals(WindowWidthSizeClass.Compact, widthSizeClassOf(0))
        assertEquals(WindowWidthSizeClass.Compact, widthSizeClassOf(360))
        assertEquals(WindowWidthSizeClass.Compact, widthSizeClassOf(599))
        assertEquals(WindowWidthSizeClass.Medium, widthSizeClassOf(600))
        assertEquals(WindowWidthSizeClass.Medium, widthSizeClassOf(720))
        assertEquals(WindowWidthSizeClass.Medium, widthSizeClassOf(839))
        assertEquals(WindowWidthSizeClass.Expanded, widthSizeClassOf(840))
        assertEquals(WindowWidthSizeClass.Expanded, widthSizeClassOf(1280))
    }

    @Test
    fun heightSizeClass_mapsBoundaryValuesCorrectly() {
        assertEquals(WindowHeightSizeClass.Compact, heightSizeClassOf(0))
        assertEquals(WindowHeightSizeClass.Compact, heightSizeClassOf(479))
        assertEquals(WindowHeightSizeClass.Medium, heightSizeClassOf(480))
        assertEquals(WindowHeightSizeClass.Medium, heightSizeClassOf(899))
        assertEquals(WindowHeightSizeClass.Expanded, heightSizeClassOf(900))
    }

    @Test
    fun useRail_isFalseOnlyForCompactWidth() {
        assertFalse(
            WindowSizeClass(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Medium).useRail
        )
        assertTrue(
            WindowSizeClass(WindowWidthSizeClass.Medium, WindowHeightSizeClass.Compact).useRail
        )
        assertTrue(
            WindowSizeClass(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Expanded).useRail
        )
    }

    @Test
    fun useListDetail_isTrueOnlyForExpandedWidth() {
        assertFalse(
            WindowSizeClass(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Medium).useListDetail
        )
        assertFalse(
            WindowSizeClass(WindowWidthSizeClass.Medium, WindowHeightSizeClass.Medium).useListDetail
        )
        assertTrue(
            WindowSizeClass(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Compact).useListDetail
        )
    }
}
