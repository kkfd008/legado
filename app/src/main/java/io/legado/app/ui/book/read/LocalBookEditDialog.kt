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
 * 编辑本地书籍：修改当前章节名与正文，写回本地文件后重新解析目录并刷新正文
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
        viewModel.load(book, ReadBook.durChapterIndex) { chapter, content ->
            binding.editTitle.setText(chapter.title)
            binding.editContent.setText(content)
        }
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
        viewModel.save(book, chapter, title, content) { index, matched ->
            ReadBook.durChapterIndex = index
            ReadBook.durChapterPos = 0
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

        fun load(book: Book, index: Int, success: (BookChapter, String) -> Unit) {
            execute {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)
                    ?: throw NoStackTraceException(context.getString(R.string.no_book))
                val rawText = LocalBook.getChapterRawText(book, chapter)
                chapter to rawText.substringAfter(chapter.title)
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
            success: (index: Int, matched: Boolean) -> Unit
        ) {
            execute {
                val start = LocalBook.saveChapterText(book, chapter, title, content)
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
                book.durChapterPos = 0
                book.durChapterTitle = toc.getOrNull(index)?.title
                book.save()
                index to matched
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                success.invoke(it.first, it.second)
            }.onError {
                AppLog.put("写入本地书籍失败\n${it.localizedMessage}", it, true)
                context.toastOnUi("${context.getString(R.string.edit_local_book_no_permission)}\n${it.localizedMessage}")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

    }

}
