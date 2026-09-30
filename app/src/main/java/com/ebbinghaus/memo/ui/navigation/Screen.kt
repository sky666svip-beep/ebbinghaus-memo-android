package com.ebbinghaus.memo.ui.navigation

/**
 * 应用全局路由定义
 */
sealed class Screen(val route: String) {
    data object MemoList : Screen("memo_list")
    data object Review : Screen("review")
    data object Settings : Screen("settings")

    /** 回收站（独立下钻页，不计入一级 Tab） */
    data object Trash : Screen("trash")

    data object MemoDetail : Screen("memo_detail/{memoId}") {
        /** 详情页路径参数名 */
        const val ARG_MEMO_ID = "memoId"

        /** 构造携带具体 ID 的跳转路由 */
        fun createRoute(memoId: Long): String = "memo_detail/$memoId"

        /** 详情页路由前缀，用于判定任意详情子路由 */
        const val ROUTE_PREFIX = "memo_detail/"
    }

    companion object {

        /**
         * 展示底栏/侧栏的一级路由集合。
         *
         * **必须惰性求值**：这里曾是一处 JVM 类静态初始化循环（JVM Spec §5.5）崩溃点。
         * 循环成因（debug 包必崩、release 因 R8 重排而不崩）：
         * 1. 若首个触碰点是嵌套 `data object`（如 `AppNavigation` 里的 `Screen.MemoList`），
         *    JVM 会**先**把 `Screen$MemoList` 标记为「正在被当前线程初始化」，**然后**才去
         *    初始化其父类 `Screen`；
         * 2. `Screen.<clinit>` 创建本 `Companion` 实例，随即读取 `MemoList.route`；
         * 3. JVM 判定 `Screen$MemoList` 的初始化是**当前线程自己发起的递归请求**，
         *    于是直接放行、不等待，读到**尚未赋值的 `INSTANCE`（null）** → NPE
         *    → `ExceptionInInitializerError`（启动即崩）。
         *
         * 惰性化后 `Screen.<clinit>` 不再触碰任何嵌套 object，循环被彻底打破。
         *
         * **线程安全**：采用 [LazyThreadSafetyMode.PUBLICATION]。该集合经 `isTopLevel`
         * 在重组期被高频读取，且并不保证只在主线程被首次触发；PUBLICATION 以 volatile + CAS
         * 发布，命中后只是一次 volatile 读，首次访问也无锁竞争，同时不像 `NONE` 那样
         * 依赖「仅主线程访问」这一假设（若将来有后台线程率先触发，`NONE` 会有可见性风险）。
         * 即便并发导致 lambda 被重复求值，`setOf(...)` 是纯函数、结果恒等，无副作用。
         */
        private val topLevelRoutes: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            setOf(MemoList.route, Review.route, Settings.route)
        }

        /**
         * 判定给定路由是否为一级 Tab 路由。
         *
         * 一级路由显示底部导航栏或侧边导航栏；二级路由（`memo_detail/{id}`）
         * 为沉浸式页面，隐藏导航条。
         *
         * @param route 当前回退栈路由，可为 null
         */
        fun isTopLevel(route: String?): Boolean = route in topLevelRoutes

        /**
         * 判定路由是否为知识点详情页。
         *
         * @param route 当前回退栈路由，可为 null
         */
        fun isMemoDetail(route: String?): Boolean =
            route?.startsWith(MemoDetail.ROUTE_PREFIX) == true
    }
}
