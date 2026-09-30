package com.ebbinghaus.memo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 知识点基础实体
 *
 * @property id 知识点唯一标识
 * @property content 知识点内容文本
 * @property notes 附加个人笔记文本
 * @property tags 标签列表
 * @property createdAt 创建毫秒时间戳
 * @property updatedAt 最后更新毫秒时间戳
 * @property deletedAt 软删除时间戳（epoch 毫秒）；null 表示存活，非 null 表示已移入回收站
 */
@Entity(tableName = "knowledge_memos")
data class KnowledgeMemoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
