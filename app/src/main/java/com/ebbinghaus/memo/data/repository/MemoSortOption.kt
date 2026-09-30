package com.ebbinghaus.memo.data.repository

/**
 * 列表排序选项
 *
 * `ordinal` 即 DAO 查询管线中的 `sortKey`，与 SQL `CASE WHEN :sortKey = N` 一一对应。
 * 排序只改变 `ORDER BY`，不参与过滤，可与搜索词、标签筛选同时生效。
 */
enum class MemoSortOption {
    /** 按创建时间倒序 */
    CREATED_DESC,

    /** 按更新时间倒序（默认） */
    UPDATED_DESC,

    /** 按复习档位倒序（高投入优先） */
    STAGE_DESC,

    /** 按到期日正序（最早到期优先） */
    DUE_ASC;

    companion object {
        /**
         * 默认排序
         *
         * **必须惰性求值**：本 `companion object` 在 `MemoSortOption.<clinit>` 期间被创建，
         * 若在此处直接读取自身枚举常量 `UPDATED_DESC`，即构成「外层类的 companion 在类初始化期
         * 引用本类常量」的静态初始化循环（与 `Screen` / `TopLevelDestination` 同型，
         * 详见 `design/CRASH_INVESTIGATION.md`）。当前 Kotlin 编译器恰好把枚举常量的赋值排在
         * 外层 `<clinit>` 之前故未暴露，但这属于**依赖代码生成顺序的隐式契约**，惰性化后不再依赖。
         *
         * **线程安全**：采用 [LazyThreadSafetyMode.PUBLICATION]，纯函数求值、结果恒等。
         */
        val DEFAULT: MemoSortOption by lazy(LazyThreadSafetyMode.PUBLICATION) {
            UPDATED_DESC
        }

        /** 由持久化的序号安全还原，越界回退默认值 */
        fun fromOrdinal(ordinal: Int): MemoSortOption =
            entries.getOrNull(ordinal) ?: DEFAULT
    }
}
