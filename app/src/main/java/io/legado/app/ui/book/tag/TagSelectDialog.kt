package io.legado.app.ui.book.tag

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.DialogBookTagPickerBinding
import io.legado.app.databinding.ItemTagSelectBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.applyTint
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
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
    private val adapter by lazy {
        object : RecyclerAdapter<BookTag, ItemTagSelectBinding>(requireContext()),
            ItemTouchCallback.Callback {

            override fun getViewBinding(parent: ViewGroup): ItemTagSelectBinding {
                return ItemTagSelectBinding.inflate(layoutInflater, parent, false)
            }

            override fun convert(
                holder: ItemViewHolder,
                binding: ItemTagSelectBinding,
                item: BookTag,
                payloads: MutableList<Any>
            ) {
                binding.run {
                    cbTag.text = item.name
                    val selected = currentTags and item.tagId > 0
                    root.isActivated = selected
                    cbTag.setTextColor(
                        if (selected) 0xFFFFFFFF.toInt()
                        else context.getCompatColor(R.color.primaryText)
                    )
                    val editTint = if (selected) 0xFFFFFFFF.toInt()
                    else context.getCompatColor(R.color.secondaryText)
                    tvEdit.setColorFilter(editTint)
                }
            }

            override fun registerListener(holder: ItemViewHolder, binding: ItemTagSelectBinding) {
                binding.tvEdit.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        showDialogFragment(TagEditDialog(it))
                    }
                }
                binding.root.setOnClickListener {
                    getItem(holder.layoutPosition)?.let { item ->
                        if (currentTags and item.tagId > 0) {
                            currentTags = currentTags and item.tagId.inv()
                        } else {
                            currentTags = currentTags or item.tagId
                        }
                        // 切换选中态视觉
                        val selected = currentTags and item.tagId > 0
                        binding.root.isActivated = selected
                        binding.cbTag.setTextColor(
                            if (selected) 0xFFFFFFFF.toInt()
                            else context.getCompatColor(R.color.primaryText)
                        )
                        val tint = if (selected) 0xFFFFFFFF.toInt()
                        else context.getCompatColor(R.color.secondaryText)
                        binding.tvEdit.setColorFilter(tint)
                    }
                }
                holder.itemView.setOnLongClickListener {
                    getItem(holder.layoutPosition)?.let {
                        showDialogFragment(TagEditDialog(it))
                    }
                    true
                }
            }

            override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
                swapItem(srcPosition, targetPosition)
                return true
            }

            override fun onClearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                viewModel.upOrder(getItems())
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
        binding.recyclerView.layoutManager =
            GridLayoutManager(requireContext(), AppConfig.tagManageSpanCount)
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
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
        lifecycleScope.launchWhenStarted {
            viewModel.tagList.conflate().collect {
                // 按排序值从大到小展示
                adapter.setItems(it.sortedByDescending { tag -> tag.order })
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