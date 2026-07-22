package io.legado.app.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssSourceEditBinding
import io.legado.app.utils.viewbindingdelegate.viewBinding

class SourceLoginActivity : VMBaseActivity<ActivityRssSourceEditBinding, SourceLoginViewModel>() {

    override val binding by viewBinding(ActivityRssSourceEditBinding::inflate)
    override val viewModel by viewModels<SourceLoginViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.initData(intent)
    }
}