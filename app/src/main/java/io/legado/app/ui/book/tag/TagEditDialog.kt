package io.legado.app.ui.book.tag

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.DialogBookTagEditBinding
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
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

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.title = if (tag == null) {
            getString(R.string.add_tag)
        } else {
            getString(R.string.edit_tag)
        }
        binding.run {
            btnDelete.isVisible = false
            tag?.let {
                tieTagName.setText(it.name)
                btnDelete.isVisible = true
                btnDelete.setOnClickListener {
                    lifecycleScope.launch {
                        withContext(IO) {
                            tag?.let { oldTag ->
                                viewModel.delete(oldTag)
                            }
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
                lifecycleScope.launch {
                    withContext(IO) {
                        tag?.let { oldTag ->
                            if (oldTag.name != tagName) {
                                oldTag.name = tagName
                                viewModel.upTag(oldTag)
                            }
                        } ?: let {
                            val nextTagId = viewModel.getNextTagId()
                            val maxOrder = (appDb.bookTagDao.maxOrder() ?: 0) + 1
                            val bookTag = BookTag(
                                tagId = nextTagId,
                                name = tagName,
                                order = maxOrder
                            )
                            viewModel.insert(bookTag)
                        }
                    }
                    dismissAllowingStateLoss()
                }
            }
        }
    }
}