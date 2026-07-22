package io.legado.app.ui.login

import android.app.Application
import android.content.Intent
import io.legado.app.base.BaseViewModel

class SourceLoginViewModel(application: Application) : BaseViewModel(application) {

    var type: String? = null
    var key: String? = null

    fun initData(intent: Intent) {
        type = intent.getStringExtra("type")
        key = intent.getStringExtra("key")
    }
}