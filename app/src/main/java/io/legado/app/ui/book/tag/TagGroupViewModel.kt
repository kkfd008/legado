package io.legado.app.ui.book.tag

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTagGroup

class TagGroupViewModel(application: Application) : BaseViewModel(application) {

    fun add(name: String, callback: () -> Unit) {
        execute {
            appDb.tagGroupDao.insert(
                BookTagGroup(
                    groupId = appDb.tagGroupDao.getNextId(),
                    name = name,
                    order = (appDb.tagGroupDao.maxOrder() ?: 0) + 1
                )
            )
        }.onFinally {
            callback()
        }
    }

    fun update(group: BookTagGroup, name: String, callback: () -> Unit) {
        execute {
            // 落库副本，避免就地修改污染 adapter 旧数据导致 DiffUtil 判定内容未变
            appDb.tagGroupDao.update(group.copy(name = name))
        }.onFinally {
            callback()
        }
    }

    fun delete(group: BookTagGroup, callback: () -> Unit) {
        execute {
            // 删除分组的同时把其下标签归为未分组（同一事务）
            appDb.tagGroupDao.deleteAndClearTags(group)
        }.onFinally {
            callback()
        }
    }

    /** 拖拽结束后按界面顺序重排，列表按 order 降序展示，第 0 项 order 最大 */
    fun upOrder(groups: List<BookTagGroup>) {
        if (groups.isEmpty()) return
        execute {
            val size = groups.size
            val updated = groups.mapIndexed { index, group ->
                group.copy(order = size - index)
            }
            appDb.tagGroupDao.update(*updated.toTypedArray())
        }
    }
}
