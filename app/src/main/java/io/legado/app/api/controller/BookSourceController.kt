package io.legado.app.api.controller

import android.text.TextUtils
import io.legado.app.api.ReturnData
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject

object BookSourceController {

    val sources: ReturnData
        get() {
            val returnData = ReturnData()
            returnData.setErrorMsg("BookSource 已移除，不再支持此功能")
            return returnData
        }

    fun saveSource(postData: String?): ReturnData {
        return ReturnData().setErrorMsg("BookSource 已移除，不再支持此功能")
    }

    fun saveSources(postData: String?): ReturnData {
        return ReturnData().setErrorMsg("BookSource 已移除，不再支持此功能")
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        return ReturnData().setErrorMsg("BookSource 已移除，不再支持此功能")
    }

    fun deleteSources(postData: String?): ReturnData {
        return ReturnData().setErrorMsg("BookSource 已移除，不再支持此功能")
    }
}