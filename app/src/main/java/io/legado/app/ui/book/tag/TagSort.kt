package io.legado.app.ui.book.tag

import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagGroup

/**
 * 标签按分组次序排列：已分组标签按「分组 order 降序 → 标签 order 降序」，
 * 未分组（groupId = 0）或所属分组已被删除的标签固定排在最后，组内仍按标签 order 降序。
 */
fun List<BookTag>.sortByTagGroup(groups: List<BookTagGroup>): List<BookTag> {
    val groupOrder = groups.associate { it.groupId to it.order }
    return sortedWith(
        compareBy<BookTag>(
            { if (it.groupId == 0L || !groupOrder.containsKey(it.groupId)) 1 else 0 },
            { -(groupOrder[it.groupId] ?: 0) },
            { -it.order }
        )
    )
}
