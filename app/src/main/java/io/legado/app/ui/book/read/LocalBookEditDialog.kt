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
        viewModel.load(book, ReadBook.durChapterIndex, pageText) { chapter, content ->
            binding.editTitle.setText(chapter.title)
            binding.editContent.setText(content)
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

        /** 载入时阅读界面当前页的原文，写回时用它定位文件中的位置 */
        private var originPageText: String = ""

        fun load(
            book: Book,
            index: Int,
            pageText: String,
            success: (BookChapter, String) -> Unit
        ) {
            execute {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)
                    ?: throw NoStackTraceException(context.getString(R.string.no_book))
                originPageText = pageText
                chapter to pageText
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                chapter = it.first
                success.invoke(it.first, it.second)
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
            execute {
                val rawText = LocalBook.getChapterRawText(book, chapter)
                val range = findPageRange(rawText, pageText)
                    ?: throw NoStackTraceException(
                        context.getString(R.string.edit_local_book_page_not_found)
                    )
                val useCrLf = rawText.contains("\r\n")
                var newRawText = rawText.replaceRange(range, toFileNewLine(content, useCrLf))
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
         * 在章节原文中定位某一页文字的位置
         * 先整段匹配，失败时用首尾片段定位（正文可能被替换规则改写过）
         */
        private fun findPageRange(rawText: String, pageText: String): IntRange? {
            val text = pageText.trim('\n', '\r')
            if (text.isEmpty()) return null
            val fullIndex = indexOfText(rawText, text, 0)
            if (fullIndex != null) return fullIndex until fullIndex + text.length
            val anchorSize = 24
            val headAnchor = text.take(anchorSize)
            val tailAnchor = text.takeLast(anchorSize)
            val start = indexOfText(rawText, headAnchor, 0) ?: return null
            val tailIndex = indexOfText(rawText, tailAnchor, start)
            val end = if (tailIndex != null && tailIndex >= start) {
                tailIndex + tailAnchor.length
            } else {
                (start + text.length).coerceAtMost(rawText.length)
            }
            return start until end
        }

        private fun indexOfText(rawText: String, text: String, fromIndex: Int): Int? {
            val index = rawText.indexOf(text, fromIndex)
            if (index >= 0) return index
            if (text.contains("\n") && rawText.contains("\r\n")) {
                val crlfIndex = rawText.indexOf(text.replace("\n", "\r\n"), fromIndex)
                if (crlfIndex >= 0) return crlfIndex
            }
            return null
        }

    }

}
