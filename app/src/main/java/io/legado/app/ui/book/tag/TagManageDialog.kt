package io.legado.app.ui.book.tag

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemBookTagManageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 书籍标签管理
 */
class TagManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: TagViewModel by viewModels()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { TagAdapter(requireContext()) }

    /** 标签文字最大宽度，防止超长名称撑出屏幕（已扣除编辑图标与内边距） */
    private val maxTagWidth by lazy {
        (resources.displayMetrics.widthPixels * 0.85f).toInt() -
                (56 * resources.displayMetrics.density).toInt()
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.tag_manage)
        initView()
        initData()
        initMenu()
    }

    private fun initView() {
        // 与标签选择页一致：取消固定列数，按文字宽度依次排列、自动换行
        binding.recyclerView.layoutManager = FlexboxLayoutManager(requireContext()).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.FLEX_START
        }
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        // 分组标题行不参与拖拽，标签只能在本组内移动
        itemTouchCallback.canDrag = { it.itemViewType != TagListItem.TYPE_GROUP }
        // FlexboxLayoutManager 非 Grid / Linear，需显式开启拖拽方向
        itemTouchCallback.dragFlagsForOtherLayout =
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        binding.tvOk.setTextColor(requireContext().accentColor)
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initData() {
        val ungroupedName = getString(R.string.tag_group_ungrouped)
        lifecycleScope.launch {
            combine(viewModel.tagList, viewModel.tagGroupList) { tags, groups ->
                // 按分组方式显示：分组标题行 + 组内标签，未分组固定排在最后
                tags.toTagGroupItems(groups, ungroupedName)
            }.catch {
                AppLog.put("标签管理界面获取标签数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.book_tag_manage)
        binding.toolBar.menu.applyTint(requireContext())
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> {
                if (viewModel.getNextTagId() > 0L) {
                    showDialogFragment(TagEditDialog())
                } else {
                    toastOnUi("标签已达上限(64个)")
                }
            }
        }
        return true
    }

    private inner class TagAdapter(context: Context) :
        RecyclerAdapter<TagListItem, ItemBookTagManageBinding>(context),
        ItemTouchCallback.Callback {

        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemBookTagManageBinding {
            return ItemBookTagManageBinding.inflate(inflater, parent, false)
        }

        override fun getItemViewType(item: TagListItem, position: Int): Int {
            return if (item is TagListItem.Group) TagListItem.TYPE_GROUP
            else TagListItem.TYPE_TAG
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookTagManageBinding,
            item: TagListItem,
            payloads: MutableList<Any>
        ) {
            val isGroup = item is TagListItem.Group
            // 流式布局：标题行整行并强制换行，标签行按文字宽度排列、自动折行
            val lp = holder.itemView.layoutParams
            val flexLp = if (lp is FlexboxLayoutManager.LayoutParams) lp
            else FlexboxLayoutManager.LayoutParams(
                lp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                lp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            )
            flexLp.isWrapBefore = isGroup
                    || getItem(holder.layoutPosition - 1) is TagListItem.Group
            flexLp.width = if (isGroup) ViewGroup.LayoutParams.MATCH_PARENT
            else ViewGroup.LayoutParams.WRAP_CONTENT
            flexLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            flexLp.flexGrow = 0f
            holder.itemView.layoutParams = flexLp

            binding.tvGroupTitle.gone(!isGroup)
            binding.tagContent.gone(isGroup)
            when (item) {
                is TagListItem.Group -> binding.tvGroupTitle.text = item.name
                is TagListItem.Tag -> {
                    binding.tvTag.maxWidth = maxTagWidth
                    binding.tvTag.text = item.bookTag.name
                }
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemBookTagManageBinding
        ) {
            binding.tvEdit.setOnClickListener {
                (getItem(holder.layoutPosition) as? TagListItem.Tag)?.let {
                    showDialogFragment(TagEditDialog(it.bookTag.copy()))
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            // 分组标题不可移动，也不允许标签跨越标题行
            val items = getItems()
            val from = minOf(srcPosition, targetPosition)
            val to = maxOf(srcPosition, targetPosition)
            if ((from..to).any { items.getOrNull(it) is TagListItem.Group }) return false
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved) {
                val tags = getItems().filterIsInstance<TagListItem.Tag>()
                val size = tags.size
                // 用副本落库，避免就地改 order 污染列表里的实体
                viewModel.upTag(*tags.mapIndexed { index, item ->
                    item.bookTag.copy(order = size - index)
                }.toTypedArray())
            }
            isMoved = false
        }
    }
}