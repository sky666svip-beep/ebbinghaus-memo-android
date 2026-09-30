package com.ebbinghaus.memo.data

import com.ebbinghaus.memo.core.util.SearchQueryTokenizer
import com.ebbinghaus.memo.data.local.dao.KnowledgeMemoDao
import com.ebbinghaus.memo.data.local.dao.ReviewTaskDao
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.repository.MemoRepositoryImpl
import com.ebbinghaus.memo.data.repository.MemoSortOption
import com.ebbinghaus.memo.data.repository.TrashSortOption
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// =====================================================================================
// 跨层契约：UI 高亮词集合 == 仓储实际查询词集合
//
// 全仓**唯一**以「记录型 DAO + 生产 MemoRepositoryImpl」实证该契约的测试。
// 其余切分测试只覆盖纯函数，测不到「仓储有没有真的用 SearchQueryTokenizer」。
// 该处曾真实出过缺陷（第 1 轮以 assertNotEquals 证伪、第 2 轮修复），故以本契约长期钉扎。
// =====================================================================================

/**
 * 记录型 DAO 替身：捕获 `MemoRepositoryImpl` 实际下发给 DAO 的 6 元关键词，
 * 用于**实证**「仓储实际参与查询的词集合」是否等于 UI 高亮词集合。
 */
private class RecordingMemoDao : KnowledgeMemoDao {
    var lastSearchKeywords: List<String> = emptyList()
    var lastTrashKeywords: List<String> = emptyList()

    override suspend fun insert(memo: KnowledgeMemoEntity): Long = memo.id
    override suspend fun insertAll(memos: List<KnowledgeMemoEntity>) {}
    override suspend fun update(memo: KnowledgeMemoEntity) {}
    override suspend fun updateNotes(memoId: Long, newNotes: String, updatedAt: Long) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? = null
    override suspend fun getMemosByIds(ids: List<Long>): List<KnowledgeMemoEntity> = emptyList()
    override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> = flowOf(emptyList())
    override suspend fun softDeleteById(id: Long, deletedAt: Long) {}
    override suspend fun softDeleteByIds(ids: List<Long>, deletedAt: Long) {}
    override suspend fun restoreById(id: Long) {}
    override suspend fun purgeTrashedBefore(cutoff: Long) {}
    override suspend fun clearTrash() {}
    override fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>> = flowOf(emptyList())
    override suspend fun getAllMemosIncludingDeleted(): List<KnowledgeMemoEntity> = emptyList()
    override suspend fun deleteAllMemos() {}

    override fun searchMemos(
        k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
        tag: String?, sortKey: Int
    ): Flow<List<KnowledgeMemoEntity>> {
        lastSearchKeywords = listOf(k1, k2, k3, k4, k5, k6)
        return flowOf(emptyList())
    }

    override suspend fun restoreByIds(ids: List<Long>): Int = 0
    override suspend fun hardDeleteByIds(ids: List<Long>): Int = 0

    override fun getTrashedMemosFiltered(
        k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
        sortKey: Int
    ): Flow<List<TrashedMemoWithProgress>> {
        lastTrashKeywords = listOf(k1, k2, k3, k4, k5, k6)
        return flowOf(emptyList())
    }
}

/** 空实现复习任务 DAO 替身（本套件不关注其行为）。 */
private class NoopReviewTaskDao : ReviewTaskDao {
    override suspend fun insert(task: ReviewTaskEntity): Long = task.id
    override suspend fun insertAll(tasks: List<ReviewTaskEntity>) {}
    override suspend fun update(task: ReviewTaskEntity) {}
    override suspend fun getTaskById(id: Long): ReviewTaskEntity? = null
    override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? = null
    override fun getDueMemosWithTasks(targetDate: LocalDate): Flow<List<MemoWithReviewTask>> =
        flowOf(emptyList())
    override suspend fun getDueMemosWithTasksSync(targetDate: LocalDate): List<MemoWithReviewTask> =
        emptyList()
    override fun getAllMemosWithTasks(): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
    override suspend fun deleteById(id: Long) {}
    override suspend fun getAllTasksSync(): List<ReviewTaskEntity> = emptyList()
    override suspend fun deleteAll() {}
}

/**
 * 跨层契约守护：列表页 / 回收站两条路径下发的关键词集合必须恒等于
 * `SearchQueryTokenizer.activeKeywords`（UI 高亮词）——对含全角空格 U+3000、
 * Tab、换行、连续空白等对抗性输入亦须一致。
 */
class MemoRepositoryTokenizerContractTest {

    private val dao = RecordingMemoDao()
    private val repository = MemoRepositoryImpl(dao, NoopReviewTaskDao())

    private fun searchSlots(raw: String): List<String> = runBlocking {
        repository.searchMemos(raw, null, MemoSortOption.DEFAULT).toList()
        dao.lastSearchKeywords
    }

    private fun trashSlots(raw: String): List<String> = runBlocking {
        repository.getTrashedMemosFiltered(raw, TrashSortOption.DEFAULT).toList()
        dao.lastTrashKeywords
    }

    @Test
    fun trashRepository_usesTokenizer_keywordSetEqualsHighlight() = runBlocking {
        val raw = "算法　信息论"
        repository.getTrashedMemosFiltered(raw, TrashSortOption.DEFAULT).toList()

        val repoKeywords = dao.lastTrashKeywords.filter { it.isNotEmpty() }
        val highlightKeywords = SearchQueryTokenizer.activeKeywords(raw)
        assertEquals("回收站查询应使用 SearchQueryTokenizer（口径一致）", highlightKeywords, repoKeywords)
    }

    /**
     * 核心契约：`MemoRepositoryImpl.searchMemos` 与 UI 高亮共用同一真源
     * （`SearchQueryTokenizer.activeKeywords`），故对含全角空格（U+3000）的输入，
     * 仓储下发关键词集合必须**恒等**于高亮词集合。
     */
    @Test
    fun listRepository_matchesHighlight_forFullWidthSpace() = runBlocking {
        val raw = "算法　信息论"
        repository.searchMemos(raw, null, MemoSortOption.DEFAULT).toList()

        val repoKeywords = dao.lastSearchKeywords.filter { it.isNotEmpty() }
        val highlightKeywords = SearchQueryTokenizer.activeKeywords(raw)

        assertEquals("高亮侧（SearchQueryTokenizer）应切出 2 词", listOf("算法", "信息论"), highlightKeywords)
        assertEquals(
            "仓储侧下发关键词必须与高亮词集合逐项相等（全角空格 U+3000 必须为分隔符）",
            highlightKeywords,
            repoKeywords
        )
        assertEquals(
            "仓储下发的 6 元参数应等于 activeKeywords 逐项补空后的结果",
            listOf("算法", "信息论", "", "", "", ""),
            dao.lastSearchKeywords
        )
    }

    /**
     * 6 词上限提示「已忽略 N 个」按 `SearchQueryTokenizer.ignoredCount` 计算，
     * 列表页仓储与提示**同源**（均走 tokenizer），故全角空格分隔的 7 词输入下
     * 必须「提示忽略 1 个、仓储实际生效 6 个」，口径一致。
     */
    @Test
    fun sixWordHint_agreesWithRepositoryEffectiveWordCount() = runBlocking {
        val raw = "a　b　c　d　e　f　g" // 全角空格分隔的 7 个词
        repository.searchMemos(raw, null, MemoSortOption.DEFAULT).toList()

        val repoKeywords = dao.lastSearchKeywords.filter { it.isNotEmpty() }
        assertEquals("UI 侧按 tokenizer 统计为 7 词 → 提示忽略 1 个", 1, SearchQueryTokenizer.ignoredCount(raw))
        assertEquals("tokenizer 生效词数应为 6（上限）", 6, SearchQueryTokenizer.activeKeywords(raw).size)
        assertEquals("仓储侧实际生效词数必须与提示口径一致（6 个，非 1 个）", 6, repoKeywords.size)
        assertEquals(
            "仓储下发关键词应等于 activeKeywords（全角空格场景口径统一）",
            SearchQueryTokenizer.activeKeywords(raw),
            repoKeywords
        )
    }

    @Test
    fun mixedWhitespace_fullWidthTabNewline_repositoryEqualsTokenizer() {
        val raw = "a　b\tc\nd" // 全角空格 + Tab + 换行 混合
        val expected = listOf("a", "b", "c", "d", "", "")
        assertEquals("列表页：混合空白须切出 4 词并补空至 6 元", expected, searchSlots(raw))
        assertEquals("回收站：同一输入须与列表页逐项一致", expected, trashSlots(raw))
    }

    @Test
    fun consecutiveFullWidthSpaces_produceNoEmptyKeywords() {
        val raw = "a　　　b" // 连续 3 个全角空格
        assertEquals(listOf("a", "b", "", "", "", ""), searchSlots(raw))
    }

    @Test
    fun fullWidthSpaceOnlyInput_yieldsSixEmptySlots() {
        val raw = "　　"
        assertEquals("纯全角空格应切出 0 词", emptyList<String>(), SearchQueryTokenizer.tokenize(raw))
        assertEquals("仓储须下发 6 元全空串（该位不启用）", listOf("", "", "", "", "", ""), searchSlots(raw))
    }

    @Test
    fun listAndTrashPaths_shareIdenticalTokenization_forArbitraryInput() {
        // 列表页与回收站两条路径必须共用同一真源 → 对任意输入 6 元结果恒等
        val samples = listOf(
            "算法　信息论", "a　b\tc\nd", "a　　　b", "　　", "", "k1 k2 k3 k4 k5 k6 k7"
        )
        for (raw in samples) {
            assertEquals("列表页与回收站口径必须一致：raw=[$raw]", searchSlots(raw), trashSlots(raw))
        }
    }
}
