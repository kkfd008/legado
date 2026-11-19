package io.legado.app.ui.book.tag

import io.legado.app.data.entities.BookTag

/**
 * 标签列表的展示项，三个标签页面（标签管理 / 选择标签 / 标签配置）共用。
 * 列表由「分组标题行 + 该组标签行」交替组成，实现按分组方式显示。
 */
sealed class TagListItem {

    /** 分组标题行，独立占满一整行，不参与拖拽、选中与编辑 */
    data class Group(val groupId: Long, val name: String) : TagListItem()

    /** 标签行；书籍数量仅标签配置页使用 */
    data class Tag(val bookTag: BookTag, val bookCount: Int = 0) : TagListItem()

    companion object {
        const val TYPE_TAG = 0
        const val TYPE_GROUP = 1
    }
}
