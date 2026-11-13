package io.legado.app.ui.book.read

import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.DialogLocalBookEditBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 编辑本地书籍：修改当前章节名与当前页正文，写回本地文件后重新解析目录并刷新正文
 * 正文只取阅读界面当前显示的这一页，写回时也只替换这一页对应的原文片段
 */
class LocalBookEditDialog : BaseDialogFragment(R.layout.dialog_local_book_edit) {

    private val binding by viewBinding(DialogLocalBookEditBinding::bind)
    private val viewModel by viewModels<LocalBookEditViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        initMenu()
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) {
            if (it) binding.rlLoading.visible() else binding.rlLoading.gone()
        }
        val book = ReadBook.book
        if (book == null) {
            requireContext().toastOnUi(R.string.no_book)
            dismiss()
            return
        }
        val pageText = currentPageText()
        if (pageText.isEmpty()) {
            requireContext().toastOnUi(R.string.edit_local_book_page_empty)
            dismiss()
            return
        }
        viewModel.load(book, ReadBook.durChapterIndex, pageText) { chapter, content, wholeChapter ->
            binding.editTitle.setText(chapter.title)
            binding.editContent.setText(content)
            if (wholeChapter) {
                binding.tvContentLabel.setText(R.string.edit_local_book_chapter_hint)
                requireContext().toastOnUi(R.string.edit_local_book_page_fallback)
            }
        }
    }

    /**
     * 取阅读界面当前页显示的文字（不含章节标题行，不含排版缩进）
     */
    private fun currentPageText(): String {
        val textChapter = ReadBook.curTextChapter ?: return ""
        val page = textChapter.getPageByReadPos(ReadBook.durChapterPos) ?: return ""
        val sb = StringBuilder()
        page.lines.forEach { line ->
            if (line.isTitle) return@forEach
            sb.append(line.text)
            if (line.isParagraphEnd) sb.append('\n')
        }
        val text = sb.toString().trim('\n', '\r')
        return text.ifEmpty { page.text.trim('\n', '\r') }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.local_book_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.menu_save) {
                save()
                true
            } else {
                false
            }
        }
    }

    private fun save() {
        val book = ReadBook.book ?: return
        val chapter = viewModel.chapter ?: return
        val title = binding.editTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            requireContext().toastOnUi(R.string.chapter_title)
            return
        }
        val content = binding.editContent.text?.toString().orEmpty()
        if (!LocalBook.canWrite(book)) {
            requireContext().toastOnUi(R.string.edit_local_book_no_permission)
            return
        }
        val pageStartPos = ReadBook.curTextChapter
            ?.getPageByReadPos(ReadBook.durChapterPos)?.chapterPosition ?: 0
        viewModel.save(book, chapter, title, content, pageStartPos) { index, matched ->
            ReadBook.durChapterIndex = index
            ReadBook.durChapterPos = pageStartPos
            ReadBook.onChapterListUpdated(book)
            requireContext().toastOnUi(R.string.edit_local_book_success)
            if (!matched) {
                requireContext().toastOnUi(R.string.edit_local_book_merged)
            }
            dismiss()
        }
    }

    class LocalBookEditViewModel(application: Application) : BaseViewModel(application) {

        val loadStateLiveData = MutableLiveData<Boolean>()
        var chapter: BookChapter? = null

        /** 编辑框里展示的、直接取自文件的原文片段，写回时用它精确定位 */
        private var originPageText: String = ""

        /** 载入时的章节原文与该页片段在原文中的位置，文件未变时直接复用，避免同文误匹配 */
        private var originRawText: String = ""
        private var originRange: IntRange? = null

        /** 当前页无法在原文中定位时退化为整章编辑 */
        private var wholeChapter: Boolean = false

        /** 整章模式下需要原样保留的标题行（含换行） */
        private var chapterPrefix: String = ""

        fun load(
            book: Book,
            index: Int,
            pageText: String,
            success: (BookChapter, String, Boolean) -> Unit
        ) {
            execute {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)
                    ?: throw NoStackTraceException(context.getString(R.string.no_book))
                val rawText = LocalBook.getChapterRawText(book, chapter)
                originRawText = rawText
                val range = findPageRange(rawText, pageText)
                if (range != null) {
                    // 编辑框直接显示原文片段，保存时整段精确匹配必定命中
                    originRange = range
                    originPageText = rawText.substring(range)
                    wholeChapter = false
                    chapterPrefix = ""
                } else {
                    // 替换规则改写了文字内容，无法定位到当前页，改为编辑整章
                    originRange = null
                    val (prefix, body) = splitTitleLine(rawText, chapter.title)
                    originPageText = body
                    wholeChapter = true
                    chapterPrefix = prefix
                }
                Triple(chapter, originPageText, wholeChapter)
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                chapter = it.first
                success.invoke(it.first, it.second, it.third)
            }.onError {
                AppLog.put("读取本地书籍正文失败\n${it.localizedMessage}", it)
                context.toastOnUi("LoadTocError:${it.localizedMessage}")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

        fun save(
            book: Book,
            chapter: BookChapter,
            title: String,
            content: String,
            readPos: Int,
            success: (index: Int, matched: Boolean) -> Unit
        ) {
            val pageText = originPageText
            val fallback = wholeChapter
            val prefix = chapterPrefix
            val cachedRawText = originRawText
            val cachedRange = originRange
            execute {
                val rawText = LocalBook.getChapterRawText(book, chapter)
                val useCrLf = rawText.contains("\r\n")
                var newRawText = if (fallback) {
                    //整章模式：标题行原样保留，正文整体替换；清空正文即可清空该章全部内容
                    prefix + toFileNewLine(content, useCrLf)
                } else {
                    //文件未变时直接复用载入时算出的位置，避免重复文本被误替换
                    val range = (if (rawText == cachedRawText && cachedRange != null) {
                        cachedRange
                    } else {
                        findPageRange(rawText, pageText)
                    }) ?: throw NoStackTraceException(
                        context.getString(R.string.edit_local_book_page_not_found)
                    )
                    rawText.replaceRange(range, toFileNewLine(content, useCrLf))
                }
                if (title != chapter.title) {
                    //替换章节标题行，标题总是在章节最前面
                    val titleIndex = newRawText.indexOf(chapter.title)
                    if (titleIndex in 0..256) {
                        newRawText = newRawText.replaceRange(
                            titleIndex, titleIndex + chapter.title.length, title
                        )
                    }
                }
                val start = LocalBook.saveChapterRawText(book, chapter, newRawText)
                val toc = LocalBook.getChapterList(book)
                appDb.runInTransaction {
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*toc.toTypedArray())
                }
                var index = toc.indexOfFirst { it.start == start }
                val matched = index >= 0
                if (!matched) {
                    //新章节名没有匹配目录规则，章节被并入上一章
                    index = toc.indexOfLast { (it.start ?: 0L) <= start }
                    if (index < 0) index = 0
                }
                book.durChapterIndex = index
                book.durChapterPos = readPos
                book.durChapterTitle = toc.getOrNull(index)?.title
                book.save()
                index to matched
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                success.invoke(it.first, it.second)
            }.onError {
                AppLog.put("写入本地书籍失败\n${it.localizedMessage}", it, true)
                context.toastOnUi(it.localizedMessage)
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

        private fun toFileNewLine(text: String, useCrLf: Boolean): String {
            val lf = text.replace("\r\n", "\n")
            return if (useCrLf) lf.replace("\n", "\r\n") else lf
        }

        /**
         * 在章节原文中定位某一页文字对应的范围。
         * 阅读界面的文字带有排版缩进、可能被替换规则改写、换行符也可能不同，
         * 而 getChapterRawText 返回的是未做缩进与替换处理的原始文本，两者不能直接用 indexOf 比较。
         * 依次尝试：整段精确匹配 → 忽略所有空白后匹配 → 忽略空白后的首尾片段定位。
         * 返回范围始终是原文中的真实下标，用于取出原文片段直接编辑。
         */
        private fun findPageRange(rawText: String, pageText: String): IntRange? {
            val text = pageText.trim('\n', '\r')
            if (text.isEmpty()) return null
            val fullIndex = rawText.indexOf(text, 0)
            if (fullIndex >= 0) return fullIndex until fullIndex + text.length
            return findPageRangeIgnoreSpace(rawText, text)
        }

        /**
         * 忽略空白字符（段首缩进、换行、CRLF、空行）后匹配，再映射回原文真实下标。
         * 这样即使段落被重新排版、插入缩进，也能定位到对应的原文片段。
         */
        private fun findPageRangeIgnoreSpace(rawText: String, pageText: String): IntRange? {
            val (rawCompact, rawIndexes) = compactText(rawText)
            val (pageCompact, _) = compactText(pageText)
            if (pageCompact.isEmpty() || rawCompact.isEmpty()) return null
            val start = rawCompact.indexOf(pageCompact)
            if (start >= 0) {
                return rawRange(rawIndexes, start, start + pageCompact.length)
            }
            //替换规则可能改写了文字，退而用首尾片段圈定范围
            val anchorSize = 12
            val head = pageCompact.take(anchorSize)
            val tail = pageCompact.takeLast(anchorSize)
            val headIndex = rawCompact.indexOf(head)
            if (headIndex < 0) return null
            val tailIndex = rawCompact.indexOf(tail, headIndex)
            val end = if (tailIndex >= headIndex) {
                tailIndex + tail.length
            } else {
                rawCompact.length
            }
            return rawRange(rawIndexes, headIndex, end)
        }

        /** 去掉所有空白字符，同时记录每个保留字符在原文中的下标 */
        private fun compactText(text: String): Pair<String, IntArray> {
            val chars = StringBuilder(text.length)
            val indexes = ArrayList<Int>(text.length)
            text.forEachIndexed { index, c ->
                if (!c.isWhitespace()) {
                    chars.append(c)
                    indexes.add(index)
                }
            }
            return chars.toString() to indexes.toIntArray()
        }

        /** 把压缩字符串的下标区间映射回原文的真实范围 */
        private fun rawRange(indexes: IntArray, from: Int, to: Int): IntRange? {
            if (from < 0 || from >= indexes.size || to <= from) return null
            val start = indexes[from]
            val end = indexes[to.coerceAtMost(indexes.size) - 1] + 1
            return if (end > start) start until end else null
        }

        /** 拆出章节原文的标题行（含换行），余下部分作为整章正文 */
        private fun splitTitleLine(rawText: String, title: String): Pair<String, String> {
            val lineEnd = rawText.indexOf('\n')
            if (lineEnd < 0) return "" to rawText
            val prefix = rawText.substring(0, lineEnd + 1)
            val firstLine = prefix.trimEnd('\n', '\r')
            return if (title.isNotBlank() && firstLine.contains(title.trim())) {
                prefix to rawText.substring(lineEnd + 1)
            } else {
                "" to rawText
            }
        }

    }

}
