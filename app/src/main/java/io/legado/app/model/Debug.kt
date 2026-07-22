package io.legado.app.model

import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.CoroutineScope

object Debug {

    var callback: Callback? = null

    interface Callback {
        fun printLog(state: Int, msg: String)
    }

    fun startDebug(scope: CoroutineScope, source: RssSource) {
        // stub
    }

    fun cancelDebug(cancel: Boolean) {
        callback = null
    }

    fun log(sourceUrl: String, msg: String, state: Int = 0) {
        callback?.printLog(state, "$sourceUrl: $msg")
    }

    fun log(msg: String) {
        callback?.printLog(0, msg)
    }
}