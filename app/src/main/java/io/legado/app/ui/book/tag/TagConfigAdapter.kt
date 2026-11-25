package io.legado.app.ui.book.tag

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ItemTagConfigBinding
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.gone

class TagConfigAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<TagListItem, ItemTagConfigBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<Long>()

    /**
     * 拖拽进行中。此时忽略 Room Flow 的新数据，
     * 否则列表会按数据库里的旧 order 重排，把正在拖动的位置弹回。
     */
    var isDragging = false
        private set

    val selection: List<BookTag>
        get() = getItems().filterIsInstance<TagListItem.Tag>()
            .filter { selected.contains(it.bookTag.tagId) }
            .map { it.bookTag }

    /** 标签总数，不含分组标题行，用于底部操作栏计数 */
    val tagCount: Int
        get() = getItems().count { it is TagListItem.Tag }

    val diffItemCallBack = object : DiffUtil.ItemCallback<TagListItem>() {

        override fun areItemsTheSame(oldItem: TagListItem, newItem: TagListItem): Boolean {
            return when {
                oldItem is TagListItem.Group && newItem is TagListItem.Group ->
                    oldItem.groupId == newItem.groupId
                oldItem is TagListItem.Tag && newItem is TagListItem.Tag ->
                    oldItem.bookTag.tagId == newItem.bookTag.tagId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TagListItem, newItem: TagListItem): Boolean {
            return when {
                oldItem is TagListItem.Group && newItem is TagListItem.Group ->
                    oldItem.name == newItem.name
                oldItem is TagListItem.Tag && newItem is TagListItem.Tag ->
                    oldItem.bookTag.name == newItem.bookTag.name
                            && oldItem.bookTag.order == newItem.bookTag.order
                            && oldItem.bookTag.hot == newItem.bookTag.hot
                            && oldItem.bookTag.groupId == newItem.bookTag.groupId
                            && oldItem.bookCount == newItem.bookCount
                else -> false
            }
        }

        override fun getChangePayload(oldItem: TagListItem, newItem: TagListItem): Any? {
            if (oldItem !is TagListItem.Tag || newItem !is TagListItem.Tag) return null
            val payload = Bundle()
            if (oldItem.bookTag.name != newItem.bookTag.name) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.bookTag.hot != newItem.bookTag.hot) {
                payload.putBoolean("upHot", true)
            }
            if (oldItem.bookCount != newItem.bookCount) {
                payload.putBoolean("upCount", true)
            }
            return if (payload.isEmpty) null else payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemTagConfigBinding {
        return ItemTagConfigBinding.inflate(inflater, parent, false)
    }

    override fun getItemViewType(item: TagListItem, position: Int): Int {
        return if (item is TagListItem.Group) TagListItem.TYPE_GROUP
        else TagListItem.TYPE_TAG
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemTagConfigBinding,
        item: TagListItem,
        payloads: MutableList<Any>
    ) {
        val isGroup = item is TagListItem.Group
        binding.groupHeader.gone(!isGroup)
        binding.tagContent.gone(isGroup)
        if (item is TagListItem.Group) {
            binding.tvGroupTitle.text = item.name
            // 未分组是虚拟分组，不允许修改/删除
            val isUngrouped = item.groupId == 0L
            binding.tvGroupEdit.gone(isUngrouped)
            binding.tvGroupDel.gone(isUngrouped)
            return
        }
        if (item !is TagListItem.Tag) return
        val tag = item.bookTag
        binding.run {
            if (payloads.isEmpty()) {
                cbSelect.isChecked = selected.contains(tag.tagId)
                tvName.text = tag.name + root.context.getString(R.string.book_count, item.bookCount)
                swtHot.isChecked = tag.hot
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "selected" -> cbSelect.isChecked = selected.contains(tag.tagId)
                            "upName", "upCount" -> tvName.text =
                                tag.name + root.context.getString(R.string.book_count, item.bookCount)
                            "upHot" -> swtHot.isChecked = tag.hot
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemTagConfigBinding) {
        binding.tvGroupEdit.setOnClickListener {
            (getItem(holder.layoutPosition) as? TagListItem.Group)?.let {
                if (it.groupId != 0L) callBack.editGroup(it.groupId)
            }
        }
        binding.tvGroupDel.setOnClickListener {
            (getItem(holder.layoutPosition) as? TagListItem.Group)?.let {
                if (it.groupId != 0L) callBack.delGroup(it.groupId)
            }
        }
        binding.cbSelect.setOnUserCheckedChangeListener { isChecked ->
            (getItem(holder.layoutPosition) as? TagListItem.Tag)?.let {
                if (isChecked) {
                    selected.add(it.bookTag.tagId)
                } else {
                    selected.remove(it.bookTag.tagId)
                }
                callBack.upCountView()
            }
        }
        binding.swtHot.setOnUserCheckedChangeListener { isChecked ->
            (getItem(holder.layoutPosition) as? TagListItem.Tag)?.let {
                // 用副本落库，避免污染当前列表里的实体对象影响 DiffUtil 判断
                callBack.update(it.bookTag.copy(hot = isChecked))
            }
        }
        binding.ivEdit.setOnClickListener {
            (getItem(holder.layoutPosition) as? TagListItem.Tag)?.let {
                callBack.edit(it.bookTag.copy())
            }
        }
        binding.ivMenuMore.setOnClickListener {
            showMenu(it, holder.layoutPosition)
        }
    }

    override fun onCurrentListChanged() {
        callBack.upCountView()
    }

    private fun showMenu(view: View, position: Int) {
        val item = getItem(position) as? TagListItem.Tag ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.tag_config_item)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_search -> callBack.search(item.bookTag)
                R.id.menu_top -> callBack.toTop(item.bookTag)
                R.id.menu_bottom -> callBack.toBottom(item.bookTag)
                R.id.menu_del -> {
                    callBack.del(item.bookTag)
                    selected.remove(item.bookTag.tagId)
                }
            }
            true
        }
        popupMenu.show()
    }

    fun selectAll() {
        getItems().filterIsInstance<TagListItem.Tag>().forEach {
            selected.add(it.bookTag.tagId)
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().filterIsInstance<TagListItem.Tag>().forEach {
            val tagId = it.bookTag.tagId
            if (!selected.remove(tagId)) {
                selected.add(tagId)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        // 分组标题不可移动，也不允许标签跨越标题行
        val items = getItems()
        val from = minOf(srcPosition, targetPosition)
        val to = maxOf(srcPosition, targetPosition)
        if ((from..to).any { items.getOrNull(it) is TagListItem.Group }) return false
        swapItem(srcPosition, targetPosition)
        isDragging = true
        return true
    }

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (!isDragging) return
        isDragging = false
        val tags = getItems().filterIsInstance<TagListItem.Tag>()
        val size = tags.size
        // 界面按 order 降序展示，拖拽后的第 0 项应保持最大 order。
        // 用 copy 生成新对象落库，不修改 adapter 持有的实体，避免污染 DiffUtil 的旧列表。
        val orderedTags = tags.mapIndexed { index, item ->
            item.bookTag.copy(order = size - index)
        }
        callBack.upOrder(orderedTags)
    }

    interface CallBack {
        fun del(tag: BookTag)
        fun edit(tag: BookTag)
        fun update(tag: BookTag)
        fun toTop(tag: BookTag)
        fun toBottom(tag: BookTag)
        fun upOrder(tags: List<BookTag>)
        fun search(tag: BookTag)
        fun editGroup(groupId: Long)
        fun delGroup(groupId: Long)
        fun upCountView()
    }

}
