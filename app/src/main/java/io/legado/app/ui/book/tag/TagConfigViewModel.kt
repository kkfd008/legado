package io.legado.app.ui.book.tag

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag

class TagConfigViewModel(app: Application) : BaseViewModel(app) {

    fun update(tag: BookTag) {
        execute {
            appDb.bookTagDao.update(tag)
        }
    }

    fun del(vararg tags: BookTag) {
        execute {
            tags.forEach {
                appDb.bookTagDao.delete(it)
            }
        }
    }

    fun setHot(tags: List<BookTag>, hot: Boolean) {
        execute {
            tags.forEach {
                it.hot = hot
                appDb.bookTagDao.update(it)
            }
        }
    }

    fun toTop(tag: BookTag) {
        execute {
            tag.order = (appDb.bookTagDao.maxOrder() ?: 0) + 1
            appDb.bookTagDao.update(tag)
        }
    }

    fun toBottom(tag: BookTag) {
        execute {
            tag.order = (appDb.bookTagDao.minOrder() ?: 0) - 1
            appDb.bookTagDao.update(tag)
        }
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
