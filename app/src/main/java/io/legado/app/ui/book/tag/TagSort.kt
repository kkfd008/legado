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

/**
 * 生成「按分组方式显示」的列表：每个分组先插入一条标题行，再按标签 order 降序插入组内标签；
 * 分组之间按分组 order 降序；未分组（groupId = 0 或所属分组已删除）固定排在最后。
 * 没有标签的分组不显示标题行。书籍数量由 [bookCount] 提供，仅标签配置页需要。
 */
fun List<BookTag>.toTagGroupItems(
    groups: List<BookTagGroup>,
    ungroupedName: String,
    bookCount: (BookTag) -> Int = { 0 }
): List<TagListItem> {
    val validGroupIds = groups.map { it.groupId }.toSet()
    // 所属分组已被删除的标签归入未分组
    val buckets = groupBy { if (it.groupId in validGroupIds) it.groupId else 0L }
    val result = mutableListOf<TagListItem>()
    fun appendGroup(groupId: Long, name: String, tags: List<BookTag>) {
        if (tags.isEmpty()) return
        result.add(TagListItem.Group(groupId, name))
        tags.sortedByDescending { it.order }.forEach { tag ->
            result.add(TagListItem.Tag(tag, bookCount(tag)))
        }
    }
    groups.sortedByDescending { it.order }.forEach { group ->
        appendGroup(group.groupId, group.name, buckets[group.groupId].orEmpty())
    }
    appendGroup(0L, ungroupedName, buckets[0L].orEmpty())
    return result
}
