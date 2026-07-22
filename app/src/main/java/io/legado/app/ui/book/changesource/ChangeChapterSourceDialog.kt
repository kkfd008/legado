package io.legado.app.ui.book.changesource

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter

class ChangeChapterSourceDialog() : BaseDialogFragment(R.layout.dialog_recycler_view) {

    constructor(name: String, author: String, chapterIndex: Int, chapterTitle: String?) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
            putInt("chapterIndex", chapterIndex)
            putString("chapterTitle", chapterTitle)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
    }

    interface CallBack {
        fun changeTo(book: Book, toc: List<BookChapter>)
    }
}