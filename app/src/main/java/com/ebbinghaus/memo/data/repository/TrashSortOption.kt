package com.ebbinghaus.memo.data.repository

/**
 * 回收站排序选项（P2-4）
 *
 * `ordinal` 即 DAO 回收站查询管线中的 `sortKey`，与 SQL `CASE WHEN :sortKey = N` 一一对应。
 * 排序只改变 `ORDER BY`，不参与过滤，可与回收站搜索词同时生效。
 *
 * 说明：回收站语义为「管理」而非「复习」，故**不提供**按档位 / 到期日排序（PRD §4.4）。
 */
enum class TrashSortOption {
    /** 按删除时间倒序（默认） */
    DELETED_DESC,

    /** 按删除时间正序 */
    DELETED_ASC,

    /** 按标签（序列化串字典序，即「首个标签」优先） */
    TAG_ASC;

    companion object {
        /**
         * 默认排序
         *
         * **必须惰性求值**：与 [MemoSortOption.DEFAULT] 同理，避免在 `TrashSortOption.<clinit>`
         * 期间读取自身枚举常量而构成静态初始化循环（依赖代码生成顺序的隐式契约）。
         *
         * **线程安全**：采用 [LazyThreadSafetyMode.PUBLICATION]，纯函数求值、结果恒等。
         */
        val DEFAULT: TrashSortOption by lazy(LazyThreadSafetyMode.PUBLICATION) {
            DELETED_DESC
        }

        /** 由持久化的序号安全还原，越界回退默认值 */
        fun fromOrdinal(ordinal: Int): TrashSortOption =
            entries.getOrNull(ordinal) ?: DEFAULT
    }
}
