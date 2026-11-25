package io.legado.app.ui.book.searchContent


import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSearchKeyword
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ChineseUtils
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class SearchContentViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        // 每页搜索结果的条数
        const val PAGE_RESULT_TOTAL = 500
    }

    var bookUrl: String = ""
    var book: Book? = null
    private var contentProcessor: ContentProcessor? = null
    var lastQuery: String = ""
    var searchResultCounts = 0
    val cacheChapterNames = hashSetOf<String>()
    val searchResultList: MutableList<SearchResult> = mutableListOf()
    var replaceEnabled = false

    // 分页搜索游标：记住下次搜索的起始位置
    private var chapterList: List<BookChapter> = emptyList()
    private var nextChapterIndex = 0
    private var nextPositionInChapter = 0
    var hasMore = true

    // 已搜索到的分页结果缓存，用于上/下翻页
    val pages = mutableListOf<List<SearchResult>>()
    var currentPage = 0

    fun initBook(bookUrl: String, success: () -> Unit) {
        this.bookUrl = bookUrl
        execute {
            book = appDb.bookDao.getBook(bookUrl)
            book?.let {
                contentProcessor = ContentProcessor.get(it.name, it.origin)
            }
        }.onSuccess {
            success.invoke()
        }
    }

    /**
     * 搜索一个章节内从 [startPosition] 开始的结果
     * @param startPosition 章节内起始字符位置
     * @param maxResults 最多返回的匹配数，达到后提前停止
     * @return 结果、本章最后匹配位置、本章是否已搜完
     */
    suspend fun searchChapter(
        query: String,
        chapter: BookChapter,
        startPosition: Int = 0,
        maxResults: Int = Int.MAX_VALUE
    ): ChapterSearchOutcome {
        val searchResultsWithinChapter: MutableList<SearchResult> = mutableListOf()
        val book = book ?: return ChapterSearchOutcome(searchResultsWithinChapter, startPosition, true)
        val chapterContent = BookHelp.getContent(book, chapter)
            ?: return ChapterSearchOutcome(searchResultsWithinChapter, startPosition, true)
        coroutineContext.ensureActive()
        chapter.title = when (AppConfig.chineseConverterType) {
            1 -> ChineseUtils.t2s(chapter.title)
            2 -> ChineseUtils.s2t(chapter.title)
            else -> chapter.title
        }
        coroutineContext.ensureActive()
        val mContent = contentProcessor!!.getContent(
            book, chapter, chapterContent, useReplace = replaceEnabled
        ).toString()
        val (positions, truncated) = searchPosition(mContent, query, startPosition, maxResults)
        positions.forEachIndexed { index, (position, matchedKeyword) ->
            coroutineContext.ensureActive()
            val construct = getResultAndQueryIndex(mContent, position, matchedKeyword)
            val result = SearchResult(
                resultCountWithinChapter = index,
                resultText = construct.second,
                chapterTitle = chapter.title,
                query = matchedKeyword,
                searchQuery = query,
                chapterIndex = chapter.index,
                queryIndexInResult = construct.first,
                queryIndexInChapter = position
            )
            searchResultsWithinChapter.add(result)
        }
        val lastPosition = positions.lastOrNull()?.first ?: startPosition
        return ChapterSearchOutcome(searchResultsWithinChapter, lastPosition, !truncated)
    }

    /**
     * 搜索关键词在内容中的位置
     * 支持多个关键字用、分割，匹配任意一个即返回（OR逻辑）
     * @param startPos 从内容中哪个字符位置开始搜索
     * @param maxResults 最多返回的匹配数，达到后提前停止
     * @return 匹配位置列表与是否被截断（截断表示本章可能还有更多匹配）
     */
    private suspend fun searchPosition(
        content: String,
        pattern: String,
        startPos: Int = 0,
        maxResults: Int = Int.MAX_VALUE
    ): Pair<List<Pair<Int, String>>, Boolean> {
        val keywords = if (pattern.contains("、")) {
            pattern.split("、").filter { it.isNotBlank() }
        } else {
            listOf(pattern)
        }
        val positionMap = linkedMapOf<Int, String>()
        var truncated = false
        outer@ for (keyword in keywords) {
            var index = content.indexOf(keyword, startPos)
            while (index >= 0) {
                coroutineContext.ensureActive()
                if (positionMap.size >= maxResults) {
                    truncated = true
                    break@outer
                }
                if (!positionMap.containsKey(index)) {
                    positionMap[index] = keyword
                }
                index = content.indexOf(keyword, index + keyword.length)
            }
        }
        return positionMap.toList().sortedBy { it.first } to truncated
    }

    /**
     * 分页搜索下一页：从上次记录的游标位置继续，最多返回 [PAGE_RESULT_TOTAL] 条
     */
    suspend fun searchPage(query: String): List<SearchResult> {
        val pageResults = mutableListOf<SearchResult>()
        var chapterIdx = nextChapterIndex
        var posInChapter = nextPositionInChapter

        val localBook = book?.isLocal == true
        while (pageResults.size < PAGE_RESULT_TOTAL && chapterIdx < chapterList.size) {
            coroutineContext.ensureActive()
            val chapter = chapterList[chapterIdx]
            // 跳过未缓存的章节（网络书）
            if (!localBook && !cacheChapterNames.contains(chapter.getFileName())) {
                chapterIdx++
                posInChapter = 0
                continue
            }
            val remaining = PAGE_RESULT_TOTAL - pageResults.size
            val outcome = searchChapter(query, chapter, posInChapter, remaining)
            pageResults.addAll(outcome.results)
            if (outcome.finished) {
                // 本章已搜完，移到下一章从头开始
                chapterIdx++
                posInChapter = 0
            } else {
                // 本章因达到上限未搜完，记录下次继续的位置
                posInChapter = outcome.lastPositionInChapter + query.length
                break
            }
        }

        nextChapterIndex = chapterIdx
        nextPositionInChapter = posInChapter
        hasMore = pageResults.size == PAGE_RESULT_TOTAL
        searchResultCounts += pageResults.size
        return pageResults
    }

    /**
     * 初始化分页搜索：加载章节列表并重置游标
     */
    suspend fun initPageSearch() {
        chapterList = appDb.bookChapterDao.getChapterList(bookUrl)
        nextChapterIndex = 0
        nextPositionInChapter = 0
        hasMore = true
        searchResultCounts = 0
    }

    private fun getResultAndQueryIndex(
        content: String,
        queryIndexInContent: Int,
        query: String
    ): Pair<Int, String> {
        // 左右移动20个字符，构建关键词周边文字，在搜索结果里显示
        // 判断段落，只在关键词所在段落内分割
        // 利用标点符号分割完整的句
        // length和设置结合，自由调整周边文字长度
        val length = 20
        var po1 = queryIndexInContent - length
        var po2 = queryIndexInContent + query.length + length
        if (po1 < 0) {
            po1 = 0
        }
        if (po2 > content.length) {
            po2 = content.length
        }
        val queryIndexInResult = queryIndexInContent - po1
        val newText = content.substring(po1, po2)
        return queryIndexInResult to newText
    }

    /**
     * 保存搜索关键字
     */
    fun saveSearchKey(key: String) {
        execute {
            appDb.bookSearchKeywordDao.get(key)?.let {
                it.usage += 1
                it.lastUseTime = System.currentTimeMillis()
                appDb.bookSearchKeywordDao.update(it)
            } ?: appDb.bookSearchKeywordDao.insert(BookSearchKeyword(key, 1))
        }
    }

    /**
     * 清除搜索关键字历史
     */
    fun clearHistory() {
        execute {
            appDb.bookSearchKeywordDao.deleteAll()
        }
    }

    fun deleteHistory(searchKeyword: BookSearchKeyword) {
        execute {
            appDb.bookSearchKeywordDao.delete(searchKeyword)
        }
    }

}

/**
 * 章节搜索结果（含分页游标信息）
 */
data class ChapterSearchOutcome(
    val results: List<SearchResult>,
    val lastPositionInChapter: Int,
    val finished: Boolean
)