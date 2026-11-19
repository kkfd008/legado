package io.legado.app.ui.book.tag

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
import io.legado.app.databinding.DialogBookTagPickerBinding
import io.legado.app.databinding.ItemTagSelectBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.applyTint
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

class TagSelectDialog() : BaseDialogFragment(R.layout.dialog_book_tag_picker),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: TagViewModel by viewModels()
    private val binding by viewBinding(DialogBookTagPickerBinding::bind)

    constructor(currentTags: Long, requestCode: Int = -1) : this() {
        arguments = Bundle().apply {
            putLong("currentTags", currentTags)
            putInt("requestCode", requestCode)
        }
    }

    private val requestCode get() = requireArguments().getInt("requestCode", -1)
    private var currentTags = 0L
    private val callBack get() = (activity as? CallBack)

    /** 单个标签的最大宽度，防止超长标签名撑出屏幕 */
    private val maxTagWidth by lazy {
        (resources.displayMetrics.widthPixels * 0.85f).toInt()
    }

    private val adapter by lazy {
        object : RecyclerAdapter<TagListItem, ItemTagSelectBinding>(requireContext()),
            ItemTouchCallback.Callback {

            override fun getViewBinding(parent: ViewGroup): ItemTagSelectBinding {
                return ItemTagSelectBinding.inflate(layoutInflater, parent, false)
            }

            override fun getItemViewType(item: TagListItem, position: Int): Int {
                return if (item is TagListItem.Group) TagListItem.TYPE_GROUP
                else TagListItem.TYPE_TAG
            }

            override fun convert(
                holder: ItemViewHolder,
                binding: ItemTagSelectBinding,
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
                binding.cbTag.gone(isGroup)
                if (item is TagListItem.Group) {
                    binding.tvGroupTitle.text = item.name
                    return
                }
                if (item !is TagListItem.Tag) return
                val tag = item.bookTag
                binding.cbTag.maxWidth = maxTagWidth
                binding.cbTag.text = tag.name
                val selected = currentTags and tag.tagId > 0
                binding.cbTag.isActivated = selected
                binding.cbTag.setTextColor(
                    if (selected) 0xFFFFFFFF.toInt()
                    else context.getCompatColor(R.color.primaryText)
                )
            }

            override fun registerListener(holder: ItemViewHolder, binding: ItemTagSelectBinding) {
                binding.cbTag.setOnClickListener {
                    val tag = (getItem(holder.layoutPosition) as? TagListItem.Tag)?.bookTag
                        ?: return@setOnClickListener
                    if (currentTags and tag.tagId > 0) {
                        currentTags = currentTags and tag.tagId.inv()
                    } else {
                        currentTags = currentTags or tag.tagId
                    }
                    val selected = currentTags and tag.tagId > 0
                    binding.cbTag.isActivated = selected
                    binding.cbTag.setTextColor(
                        if (selected) 0xFFFFFFFF.toInt()
                        else context.getCompatColor(R.color.primaryText)
                    )
                }
                // 长按交给 ItemTouchHelper 拖拽排序
                holder.itemView.setOnLongClickListener(null)
            }

            override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
                // 分组标题不可移动，也不允许标签跨越标题行
                val items = getItems()
                val from = minOf(srcPosition, targetPosition)
                val to = maxOf(srcPosition, targetPosition)
                if ((from..to).any { items.getOrNull(it) is TagListItem.Group }) return false
                swapItem(srcPosition, targetPosition)
                return true
            }

            override fun onClearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                // 用副本落库，避免就地改 order 污染列表里的实体
                viewModel.upOrder(getItems().filterIsInstance<TagListItem.Tag>().map {
                    it.bookTag.copy()
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        currentTags = requireArguments().getLong("currentTags", 0L)
        initToolbar()
        initRecyclerView()
        initBottomButtons()
        initData()
        initMenu()
    }

    private fun initToolbar() {
        binding.toolBar.run {
            setBackgroundColor(primaryColor)
            title = getString(R.string.tag_select)
        }
    }

    private fun initRecyclerView() {
        // 不再按「每行数量」固定分列，标签按文字宽度依次排列、自动换行
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
    }

    private fun initBottomButtons() {
        binding.tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvOk.setTextColor(requireContext().accentColor)
        binding.tvOk.setOnClickListener {
            callBack?.upTags(requestCode, currentTags)
            dismissAllowingStateLoss()
        }
    }

    private fun initData() {
        val ungroupedName = getString(R.string.tag_group_ungrouped)
        lifecycleScope.launchWhenStarted {
            combine(viewModel.tagList, viewModel.tagGroupList) { tags, groups ->
                // 按分组方式显示：分组标题行 + 组内标签，未分组固定排在最后
                tags.toTagGroupItems(groups, ungroupedName)
            }.conflate().collect {
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
            R.id.menu_add -> showDialogFragment(
                TagEditDialog()
            )

            R.id.menu_ok -> {
                callBack?.upTags(requestCode, currentTags)
                dismissAllowingStateLoss()
            }
        }
        return true
    }

    interface CallBack {
        fun upTags(requestCode: Int, tags: Long)
    }
}
