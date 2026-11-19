package io.legado.app.ui.book.tag

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.databinding.DialogBookTagEditBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TagEditDialog() : BaseDialogFragment(R.layout.dialog_book_tag_edit) {

    constructor(bookTag: BookTag) : this() {
        arguments = Bundle().apply {
            putParcelable("bookTag", bookTag)
        }
    }

    private val tag by lazy { arguments?.getParcelable<BookTag>("bookTag") }
    private val viewModel: TagViewModel by viewModels()
    private val binding by viewBinding(DialogBookTagEditBinding::bind)

    private var groups: List<BookTagGroup> = emptyList()
    private val groupNames = ArrayList<String>()
    private var selectionRestored = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = if (tag == null) {
            getString(R.string.add_tag)
        } else {
            getString(R.string.edit_tag)
        }
        initGroupSpinner()
        binding.run {
            btnDelete.isVisible = false
            tag?.let {
                tieTagName.setText(it.name)
                btnDelete.isVisible = true
                btnDelete.setOnClickListener {
                    lifecycleScope.launch {
                        withContext(IO) {
                            tag?.let { oldTag -> viewModel.delete(oldTag) }
                        }
                        dismissAllowingStateLoss()
                    }
                }
            }
            btnCancel.setOnClickListener { dismissAllowingStateLoss() }
            btnOk.setOnClickListener {
                val tagName = tieTagName.text.toString().trim()
                if (tagName.isEmpty()) {
                    dismissAllowingStateLoss()
                    return@setOnClickListener
                }
                // Check duplicate name
                val existing = appDb.bookTagDao.getByName(tagName)
                if (existing != null && existing.tagId != tag?.tagId) {
                    toastOnUi(R.string.name_exist)
                    return@setOnClickListener
                }
                val groupId = selectedGroupId()
                lifecycleScope.launch {
                    withContext(IO) {
                        tag?.let { oldTag ->
                            // 排序由列表拖拽/菜单维护，编辑页只修改名称和分组。
                            // arguments 中的 Parcelable 与列表持有的是同一对象，必须落库副本，
                            // 就地赋值会污染 adapter 旧数据，令 DiffUtil 判定内容未变、界面不刷新。
                            if (oldTag.name != tagName || oldTag.groupId != groupId) {
                                viewModel.upTag(oldTag.copy(name = tagName, groupId = groupId))
                            }
                        } ?: let {
                            val bookTag = BookTag(
                                tagId = viewModel.getNextTagId(),
                                name = tagName,
                                order = (appDb.bookTagDao.maxOrder() ?: 0) + 1,
                                groupId = groupId
                            )
                            viewModel.insert(bookTag)
                        }
                    }
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    /** 下拉第 0 项固定为「未分组」，其余按分组 order 从大到小 */
    private fun initGroupSpinner() {
        val spinnerAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, groupNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spTagGroup.adapter = spinnerAdapter
        lifecycleScope.launch {
            appDb.tagGroupDao.flowAll().flowOn(IO).conflate().collect { list ->
                groups = list
                groupNames.clear()
                groupNames.add(getString(R.string.tag_group_ungrouped))
                groupNames.addAll(list.map { it.name })
                spinnerAdapter.notifyDataSetChanged()
                if (!selectionRestored) {
                    selectionRestored = true
                    val groupId = tag?.groupId ?: 0L
                    val position = list.indexOfFirst { it.groupId == groupId } + 1
                    binding.spTagGroup.setSelection(position.coerceIn(0, groupNames.lastIndex))
                }
            }
        }
    }

    private fun selectedGroupId(): Long {
        val position = binding.spTagGroup.selectedItemPosition
        if (position <= 0) return 0L
        return groups.getOrNull(position - 1)?.groupId ?: 0L
    }
}
