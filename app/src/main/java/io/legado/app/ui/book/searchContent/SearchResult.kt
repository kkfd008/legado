package io.legado.app.ui.book.searchContent

import android.text.Spanned
import androidx.core.text.HtmlCompat
import io.legado.app.help.config.AppConfig

data class SearchResult(
    val resultCount: Int = 0,
    val resultCountWithinChapter: Int = 0,
    val resultText: String = "",
    val chapterTitle: String = "",
    val query: String = "",
    val searchQuery: String = "",
    val pageSize: Int = 0,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val queryIndexInResult: Int = 0,
    val queryIndexInChapter: Int = 0
) {

    fun getHtmlCompat(textColor: String, accentColor: String): Spanned {
        // 优先使用搜索时记录的精确位置（queryIndexInResult），因为替换规则/简繁转换后
        // resultText.indexOf(query) 可能返回 -1 或指向错误位置，导致 substring 越界崩溃。
        // 同时做边界校验，任何非法位置都退化为不高亮整段文字。
        val queryIndexInSurrounding = queryIndexInResult.takeIf {
            query.isNotBlank() && it >= 0 && it + query.length <= resultText.length
        } ?: resultText.indexOf(query)
        val canHighlight = query.isNotBlank()
                && queryIndexInSurrounding >= 0
                && queryIndexInSurrounding + query.length <= resultText.length
        return if (canHighlight) {
            val leftString = resultText.substring(0, queryIndexInSurrounding)
            val rightString =
                resultText.substring(queryIndexInSurrounding + query.length, resultText.length)
            
            // 检查是否为墨水屏模式
            val html = if (AppConfig.isEInkMode) {
                // 墨水屏模式：使用下划线
                buildString {
                    append("<u>${chapterTitle}</u>")
                    append("<br>")
                    append(leftString)
                    append("<u>${query}</u>")
                    append(rightString)
                }
            } else {
                // 普通模式：使用颜色
                buildString {
                    append(chapterTitle.colorTextForHtml(accentColor))
                    append("<br>")
                    append(leftString.colorTextForHtml(textColor))
                    append(query.colorTextForHtml(accentColor))
                    append(rightString.colorTextForHtml(textColor))
                }
            }
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        } else {
            val html = if (AppConfig.isEInkMode) {
                resultText
            } else {
                resultText.colorTextForHtml(textColor)
            }
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun String.colorTextForHtml(textColor: String) =
        "<font color=#${textColor}>$this</font>"

}
