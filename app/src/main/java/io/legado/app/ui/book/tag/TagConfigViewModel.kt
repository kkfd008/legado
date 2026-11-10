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
        if (tags.isEmpty()) return
        execute {
            // 用 SQL 批量更新，不修改 adapter 当前持有的实体对象；
            // 否则 Room Flow 重新发射时 DiffUtil 会判定新旧内容相同，热门开关不刷新。
            appDb.bookTagDao.updateHot(tags.map { it.tagId }, hot)
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
