package com.ebbinghaus.memo.ui.navigation

import com.ebbinghaus.memo.data.repository.MemoSortOption
import com.ebbinghaus.memo.data.repository.TrashSortOption
import com.ebbinghaus.memo.core.model.ReviewStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * 导航路由常量「类静态初始化循环」回归测试（纯 JVM，毫秒级）
 *
 * ## 背景：一次真实的 debug 包启动崩溃
 *
 * ```
 * E AndroidRuntime: java.lang.ExceptionInInitializerError
 * E AndroidRuntime:   at com.ebbinghaus.memo.ui.navigation.AppNavigationKt.AppNavigation(AppNavigation.kt:215)
 * E AndroidRuntime: Caused by: java.lang.NullPointerException:
 * E AndroidRuntime:   Attempt to invoke virtual method 'java.lang.String ...Screen$MemoList.getRout...'
 * E AndroidRuntime:   at com.ebbinghaus.memo.ui.navigation.Screen.<clinit>(Screen.kt:29)
 * ```
 *
 * 根因是 **JVM 类静态初始化循环**（JVM Spec §5.5）：
 *
 * 1. 首帧触碰 `Screen.MemoList` → JVM 开始初始化 `Screen$MemoList`，并**先把它标记为
 *    「正在被当前线程初始化」**，随后才去初始化其父类 `Screen`；
 * 2. `Screen.<clinit>` 创建 `Companion` 实例 → `Companion.<clinit>` 读取
 *    `Screen$MemoList.INSTANCE.route`；
 * 3. JVM 发现 `Screen$MemoList`「正被当前线程初始化」→ 判定为递归请求、**直接放行不等待**，
 *    于是读到**仍然为 null 的 `INSTANCE`** → NPE → `ExceptionInInitializerError`。
 *
 * release 不崩是因为 R8 重排 / 内联了这段静态初始化，把循环打破 —— 该 bug 只在未优化的
 * debug 包暴露，与用户反馈「release 正常、debug 必崩」完全吻合。
 *
 * ## 为什么之前的 306 条用例没抓到
 *
 * `app/src/test` 与 `core/src/test` 中**没有任何一条**用例触碰过 `Screen` 或
 * `TopLevelDestination`（已全量核验），属测试盲区。且该崩溃**只在「先触碰嵌套 object、
 * 后触碰外层类」的顺序下才出现**：若先触碰 `Screen.isTopLevel(...)`（即先触发 `Companion`），
 * JVM 会正常完成 `Screen$MemoList` 的初始化，反而不崩。
 *
 * 为让回归钉扎**与用例执行顺序无关**，本套件用 [isolatedClassLoader] 在**全新类加载器**
 * 中做初始化，保证每次都是「干净」的静态状态，从而稳定复现该循环。
 */
class NavigationRouteInitRegressionTest {

    // -------------------------------------------------------------------------
    // 一、回归钉扎：在全新类加载器中优先触碰嵌套 object（顺序无关、必然复现）
    // -------------------------------------------------------------------------

    /**
     * 【核心回归】`Screen$MemoList` 作为首个触碰点时不得抛出 `ExceptionInInitializerError`。
     *
     * 修复前：JVM 判定递归初始化请求 → `INSTANCE` 为 null → NPE → `ExceptionInInitializerError`。
     * 修复后：`topLevelRoutes` 惰性化，`Screen.<clinit>` 不再触碰嵌套 object。
     */
    @Test
    fun screen_nestedObjectTouchedFirst_initializesCleanly() {
        val loader = isolatedClassLoader()
        val memoListClass = assertInitializesCleanly(SCREEN_MEMO_LIST, loader)

        val instance = memoListClass.getField("INSTANCE").get(null)
        assertNotNull(
            "Screen\$MemoList.INSTANCE 不应为 null（静态初始化循环会在初始化途中被读到 null）",
            instance
        )
        assertEquals(
            "memo_list",
            memoListClass.getMethod("getRoute").invoke(instance) as String
        )
    }

    /**
     * 【核心回归】`TopLevelDestination$Library` 作为首个触碰点时不得初始化失败。
     *
     * 修复前此处有**两种**失败形态，任何一种都会让本用例变红：
     * 1. 经由 `Screen.MemoList.route` 触发 `Screen` 的初始化循环 → `ExceptionInInitializerError`；
     * 2. 若 `Screen` 已先行初始化成功，`TopLevelDestination.Companion.<clinit>` 仍会读到
     *    尚未赋值的 `Library/Review/Settings.INSTANCE`（null），使 `entries` 变成
     *    `[null, null, null]` —— 这正是历史上 `NoClassDefFoundError: Could not initialize
     *    class TopLevelDestination` 与「导航测试连带失败」的真正成因。
     */
    @Test
    fun topLevelDestination_nestedObjectTouchedFirst_initializesCleanly() {
        val loader = isolatedClassLoader()
        val libraryClass = assertInitializesCleanly(TLD_LIBRARY, loader)

        assertNotNull(
            "TopLevelDestination\$Library.INSTANCE 不应为 null",
            libraryClass.getField("INSTANCE").get(null)
        )

        val entries = isolatedEntries(loader)
        assertEquals("一级 Tab 数量", 3, entries.size)
        entries.forEachIndexed { index, entry ->
            assertNotNull(
                "entries[$index] 不应为 null（companion 在类初始化期读到了尚未赋值的嵌套 object）",
                entry
            )
        }
        assertEquals(
            "一级 Tab 展示顺序即 entries 顺序",
            listOf("memo_list", "review", "settings"),
            entries.map { it!!.javaClass.getMethod("getRoute").invoke(it) as String }
        )
    }

    // -------------------------------------------------------------------------
    // 二、行为契约：既有调用点依赖的语义，不得因修复而改变
    // -------------------------------------------------------------------------

    @Test
    fun isTopLevel_returnsTrueOnlyForTopLevelRoutes() {
        assertTrue(Screen.isTopLevel("memo_list"))
        assertTrue(Screen.isTopLevel("review"))
        assertTrue(Screen.isTopLevel("settings"))
        assertFalse(Screen.isTopLevel("trash"))
        assertFalse(Screen.isTopLevel("memo_detail/5"))
        assertFalse(Screen.isTopLevel(null))
    }

    @Test
    fun isMemoDetail_matchesDetailRoutePrefixOnly() {
        assertTrue(Screen.isMemoDetail("memo_detail/5"))
        assertTrue(Screen.isMemoDetail("memo_detail/0"))
        assertFalse(Screen.isMemoDetail("memo_list"))
        assertFalse(Screen.isMemoDetail("review"))
        assertFalse(Screen.isMemoDetail(null))
    }

    @Test
    fun memoDetail_createRoute_buildsTypedRoute() {
        assertEquals("memo_detail/5", Screen.MemoDetail.createRoute(5L))
        assertEquals("memo_detail/0", Screen.MemoDetail.createRoute(0L))
        assertEquals("memo_detail/", Screen.MemoDetail.ROUTE_PREFIX)
        assertEquals("memoId", Screen.MemoDetail.ARG_MEMO_ID)
    }

    @Test
    fun topLevelDestination_entries_preservesTabOrder() {
        assertEquals(
            "顺序即一级 Tab 展示顺序，不可调整",
            listOf(
                TopLevelDestination.Library,
                TopLevelDestination.Review,
                TopLevelDestination.Settings
            ),
            TopLevelDestination.entries
        )
    }

    @Test
    fun topLevelDestination_fromRoute_resolvesKnownRoutesOnly() {
        assertSame(TopLevelDestination.Review, TopLevelDestination.fromRoute("review"))
        assertSame(TopLevelDestination.Library, TopLevelDestination.fromRoute("memo_list"))
        assertSame(TopLevelDestination.Settings, TopLevelDestination.fromRoute("settings"))
        assertNull(TopLevelDestination.fromRoute("trash"))
        assertNull(TopLevelDestination.fromRoute("memo_detail/5"))
        assertNull(TopLevelDestination.fromRoute(null))
    }

    @Test
    fun topLevelDestination_isBadgeTarget_matchesReviewOnly() {
        assertTrue(TopLevelDestination.isBadgeTarget(TopLevelDestination.Review))
        assertFalse(TopLevelDestination.isBadgeTarget(TopLevelDestination.Library))
        assertFalse(TopLevelDestination.isBadgeTarget(TopLevelDestination.Settings))
    }

    // -------------------------------------------------------------------------
    // 三、同类模式扫描：enum + companion 在类初始化期引用自身常量
    //
    // 以下三处与崩溃点同型（外层「类」的 companion object 在 <clinit> 中读取本类的
    // 常量 / 嵌套对象）。当前 Kotlin 编译器把 enum 常量的赋值排在外层 <clinit> 之前，
    // 因此侥幸不崩 —— 但这属于**依赖代码生成顺序的隐式契约**，此处钉扎以防回归。
    // -------------------------------------------------------------------------

    @Test
    fun enumCompanion_selfReferenceDuringInit_resolvesConstant() {
        assertEquals(MemoSortOption.UPDATED_DESC, MemoSortOption.DEFAULT)
        assertEquals(MemoSortOption.UPDATED_DESC, MemoSortOption.fromOrdinal(1))
        assertEquals(MemoSortOption.UPDATED_DESC, MemoSortOption.fromOrdinal(Int.MAX_VALUE))

        assertEquals(TrashSortOption.DELETED_DESC, TrashSortOption.DEFAULT)
        assertEquals(TrashSortOption.DELETED_DESC, TrashSortOption.fromOrdinal(0))
        assertEquals(TrashSortOption.DELETED_DESC, TrashSortOption.fromOrdinal(-1))
    }

    @Test
    fun reviewStage_fromLevel_fallsBackToStageOne() {
        assertEquals(ReviewStage.STAGE_1, ReviewStage.fromLevel(1))
        assertEquals(ReviewStage.LONG_TERM_60, ReviewStage.fromLevel(7))
        assertEquals(
            "越界回退第一档（引用位于函数体内，惰性求值，不构成初始化循环）",
            ReviewStage.STAGE_1,
            ReviewStage.fromLevel(99)
        )
    }

    // -------------------------------------------------------------------------
    // 私有工具
    // -------------------------------------------------------------------------

    /**
     * 在全新类加载器中加载并**初始化**指定类；任何初始化异常都转为带说明的 [AssertionError]。
     *
     * @param binaryName 类的二进制名（含 `$` 分隔的嵌套类名）
     * @param loader 隔离的类加载器
     * @return 已成功初始化的 `Class` 对象
     */
    private fun assertInitializesCleanly(binaryName: String, loader: ClassLoader): Class<*> =
        try {
            Class.forName(binaryName, true, loader)
        } catch (throwable: Throwable) {
            throw AssertionError(
                "【回归钉扎】$binaryName 在全新类加载器中初始化失败：" +
                    "${throwable.javaClass.name}: ${throwable.message}\n" +
                    "这说明外层类的 companion object 在 <clinit> 期读取了尚未完成初始化的嵌套 object，" +
                    "构成 JVM 类静态初始化循环（JVM Spec §5.5）。",
                throwable
            )
        }

    /**
     * 读取隔离类加载器中 `TopLevelDestination.Companion.entries` 的内容。
     *
     * 走反射是必须的：隔离加载器中的 `TopLevelDestination` 与本用例编译期引用的
     * 是**两个不同的 `Class` 对象**，不能强转。
     */
    private fun isolatedEntries(loader: ClassLoader): List<Any?> {
        val tldClass = assertInitializesCleanly(TOP_LEVEL_DESTINATION, loader)
        val companion = tldClass.getField("Companion").get(null)
        @Suppress("UNCHECKED_CAST")
        return companion.javaClass.getMethod("getEntries").invoke(companion) as List<Any?>
    }

    /**
     * 构造与当前测试 JVM **完全隔离**的类加载器，保证被加载类的静态状态是全新的。
     *
     * 父加载器传 `null`（即 bootstrap 类加载器）：
     * - 既能共享 `java.*` 等 JDK 平台类（`String` 因此可跨加载器直接比较）；
     * - 又不会共享 `kotlin.*` 与应用类，从而真正隔离 `Screen` / `TopLevelDestination` 的静态字段。
     *
     * 注意：此处**不能**用 `ClassLoader.getPlatformClassLoader()` —— 编译期 bootclasspath 用的是
     * Android `java.base` 桩（core-for-system-modules.jar），其中没有该 API。
     */
    private fun isolatedClassLoader(): ClassLoader =
        URLClassLoader(resolveClassPathUrls(), null)

    /**
     * 解析当前测试 JVM 的完整类路径。
     *
     * Gradle 在类路径过长时会把 `-cp` 改写成一个「仅含 `Class-Path` 清单的 jar」，
     * 因此这里需要递归展开 jar 清单里声明的条目，否则隔离加载器会找不到类。
     */
    private fun resolveClassPathUrls(): Array<URL> {
        val urls = LinkedHashSet<URL>()
        val pending = ArrayDeque<String>()
        val visited = HashSet<String>()

        System.getProperty("java.class.path")
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .forEach { pending.addLast(it) }

        while (pending.isNotEmpty()) {
            val entry = pending.removeFirst()
            if (!visited.add(entry)) continue

            val file = File(entry)
            urls.add(file.toURI().toURL())
            if (!file.isFile || !file.name.endsWith(".jar", ignoreCase = true)) continue

            // 展开「Class-Path 清单 jar」中声明的条目
            runCatching {
                JarFile(file).use { jar ->
                    val declared = jar.manifest?.mainAttributes?.getValue("Class-Path").orEmpty()
                    declared.split(" ")
                        .filter { it.isNotBlank() }
                        .forEach { ref ->
                            val resolved = if (ref.startsWith("file:")) {
                                File(URI.create(ref))
                            } else {
                                File(file.parentFile, ref)
                            }
                            pending.addLast(resolved.absolutePath)
                        }
                }
            }
        }
        return urls.toTypedArray()
    }

    private companion object {
        const val SCREEN_MEMO_LIST = "com.ebbinghaus.memo.ui.navigation.Screen\$MemoList"
        const val TOP_LEVEL_DESTINATION = "com.ebbinghaus.memo.ui.navigation.TopLevelDestination"
        const val TLD_LIBRARY = "com.ebbinghaus.memo.ui.navigation.TopLevelDestination\$Library"
    }
}
