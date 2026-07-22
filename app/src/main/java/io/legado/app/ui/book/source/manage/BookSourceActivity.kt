package io.legado.app.ui.book.source.manage

import android.os.Bundle
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityBookSourceManageBinding
import io.legado.app.utils.viewbindingdelegate.viewBinding

class BookSourceActivity : BaseActivity<ActivityBookSourceManageBinding>() {

    override val binding by viewBinding(ActivityBookSourceManageBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
    }
}