package io.legado.app.ui.book.tag

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ItemTagConfigBinding

data class TagConfigItem(val tag: BookTag, val bookCount: Int)

class TagConfigAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<TagConfigItem, ItemTagConfigBinding>(context) {

    private val selected = linkedSetOf<Long>()

    val selection: List<BookTag>
        get() = getItems().filter { selected.contains(it.tag.tagId) }.map { it.tag }

    val diffItemCallBack = object : DiffUtil.ItemCallback<TagConfigItem>() {

        override fun areItemsTheSame(oldItem: TagConfigItem, newItem: TagConfigItem): Boolean {
            return oldItem.tag.tagId == newItem.tag.tagId
        }

        override fun areContentsTheSame(oldItem: TagConfigItem, newItem: TagConfigItem): Boolean {
            return oldItem.tag.name == newItem.tag.name
                    && oldItem.tag.order == newItem.tag.order
                    && oldItem.tag.hot == newItem.tag.hot
                    && oldItem.bookCount == newItem.bookCount
        }

        override fun getChangePayload(oldItem: TagConfigItem, newItem: TagConfigItem): Any? {
            val payload = Bundle()
            if (oldItem.tag.name != newItem.tag.name) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.tag.hot != newItem.tag.hot) {
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

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemTagConfigBinding,
        item: TagConfigItem,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                cbSelect.isChecked = selected.contains(item.tag.tagId)
                tvName.text = item.tag.name
                tvCount.text = root.context.getString(R.string.book_count, item.bookCount)
                swtHot.isChecked = item.tag.hot
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "selected" -> cbSelect.isChecked = selected.contains(item.tag.tagId)
                            "upName" -> tvName.text = item.tag.name
                            "upCount" -> tvCount.text =
                                root.context.getString(R.string.book_count, item.bookCount)
                            "upHot" -> swtHot.isChecked = item.tag.hot
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemTagConfigBinding) {
        binding.cbSelect.setOnUserCheckedChangeListener { isChecked ->
            getItem(holder.layoutPosition)?.let {
                if (isChecked) {
                    selected.add(it.tag.tagId)
                } else {
                    selected.remove(it.tag.tagId)
                }
                callBack.upCountView()
            }
        }
        binding.swtHot.setOnUserCheckedChangeListener { isChecked ->
            getItem(holder.layoutPosition)?.let {
                it.tag.hot = isChecked
                callBack.update(it.tag)
            }
        }
        binding.ivEdit.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.edit(it.tag)
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
        val item = getItem(position) ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.tag_config_item)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_top -> callBack.toTop(item.tag)
                R.id.menu_bottom -> callBack.toBottom(item.tag)
                R.id.menu_del -> {
                    callBack.del(item.tag)
                    selected.remove(item.tag.tagId)
                }
            }
            true
        }
        popupMenu.show()
    }

    fun selectAll() {
        getItems().forEach {
            selected.add(it.tag.tagId)
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().forEach {
            if (!selected.remove(it.tag.tagId)) {
                selected.add(it.tag.tagId)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    interface CallBack {
        fun del(tag: BookTag)
        fun edit(tag: BookTag)
        fun update(tag: BookTag)
        fun toTop(tag: BookTag)
        fun toBottom(tag: BookTag)
        fun upCountView()
    }

}
