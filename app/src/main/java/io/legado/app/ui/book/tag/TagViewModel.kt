package io.legado.app.ui.book.tag

import androidx.lifecycle.ViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag

class TagViewModel : ViewModel() {

    val tagList = appDb.bookTagDao.flowSelect()

    fun upTag(vararg bookTag: BookTag) {
        appDb.bookTagDao.run {
            bookTag.forEach {
                update(it)
            }
        }
    }

    fun upOrder(bookTagList: List<BookTag>) {
        appDb.bookTagDao.run {
            bookTagList.forEachIndexed { index, bookTag ->
                bookTag.order = index
                update(bookTag)
            }
        }
    }

    fun delete(vararg bookTag: BookTag) {
        appDb.bookTagDao.run {
            bookTag.forEach {
                delete(it)
            }
        }
    }

    fun deleteTag(tagId: Long) {
        appDb.bookTagDao.delete(tagId)
    }

    fun insert(bookTag: BookTag) {
        appDb.bookTagDao.insert(bookTag)
    }

    fun getNextTagId(): Long {
        var id = 1L
        val idsSum = appDb.bookTagDao.idsSum
        while (id and idsSum != 0L) {
            id = id shl 1
        }
        return id
    }
}