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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemBookTagManageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
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
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        binding.tvOk.setTextColor(requireContext().accentColor)
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            viewModel.tagList.catch {
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
        RecyclerAdapter<BookTag, ItemBookTagManageBinding>(context),
        ItemTouchCallback.Callback {

        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemBookTagManageBinding {
            return ItemBookTagManageBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookTagManageBinding,
            item: BookTag,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                tvTag.text = item.name
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemBookTagManageBinding
        ) {
            binding.run {
                tvEdit.setOnClickListener {
                    getItem(holder.layoutPosition)?.let { bookTag ->
                        showDialogFragment(
                            TagEditDialog(bookTag)
                        )
                    }
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved) {
                for ((index, item) in getItems().withIndex()) {
                    item.order = index + 1
                }
                viewModel.upTag(*getItems().toTypedArray())
            }
            isMoved = false
        }
    }
}