package io.legado.app.ui.book.tag

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.databinding.DialogTagGroupEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class TagGroupEditDialog() : BaseDialogFragment(R.layout.dialog_tag_group_edit) {

    constructor(tagGroup: BookTagGroup) : this() {
        arguments = Bundle().apply {
            putParcelable("tagGroup", tagGroup)
        }
    }

    private val tagGroup by lazy {
        @Suppress("DEPRECATION")
        arguments?.getParcelable<BookTagGroup>("tagGroup")
    }
    private val viewModel: TagGroupViewModel by viewModels()
    private val binding by viewBinding(DialogTagGroupEditBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = if (tagGroup == null) {
            getString(R.string.add_group)
        } else {
            getString(R.string.tag_group_edit)
        }
        binding.run {
            btnDelete.isVisible = tagGroup != null
            tagGroup?.let { tieTagGroupName.setText(it.name) }
            btnCancel.setOnClickListener { dismissAllowingStateLoss() }
            btnDelete.setOnClickListener {
                alert(R.string.delete, R.string.sure_del) {
                    yesButton {
                        tagGroup?.let { viewModel.delete(it) { dismissAllowingStateLoss() } }
                    }
                    noButton()
                }
            }
            btnOk.setOnClickListener {
                val name = tieTagGroupName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toastOnUi(R.string.tag_group_name_empty)
                    return@setOnClickListener
                }
                val existing = appDb.tagGroupDao.getByName(name)
                if (existing != null && existing.groupId != tagGroup?.groupId) {
                    toastOnUi(R.string.name_exist)
                    return@setOnClickListener
                }
                tagGroup?.let {
                    viewModel.update(it, name) { dismissAllowingStateLoss() }
                } ?: let {
                    viewModel.add(name) { dismissAllowingStateLoss() }
                }
            }
        }
    }
}
