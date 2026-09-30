package com.ebbinghaus.memo.core.model

/**
 * 艾宾浩斯复习评级与交互操作
 */
enum class ReviewRating {
    FORGET,          // 忘记：回退1档（若已是第1档则保持第1档，长周期退回第6档）
    VAGUE,           // 模糊：保持当前档位不变
    REMEMBER,        // 记住：前进1档；第6档记住进入长周期60天；长周期记住保持60天
    DEFAULT_REVIEWED,// 已复习（缺省操作）：默认等同于模糊处理
    SKIP             // 跳过：不改变档位，该任务自动延后1天复习
}
