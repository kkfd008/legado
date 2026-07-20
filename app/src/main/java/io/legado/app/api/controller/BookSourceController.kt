package io.legado.app.api.controller


import android.text.TextUtils
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

object BookSourceController {

    val sources: ReturnData
        get() {
            val bookSources = appDb.bookSourceDao.all
            val returnData = ReturnData()
            return if (bookSources.isEmpty()) {
                returnData.setErrorMsg("设备源列表为空")
            } else returnData.setData(bookSources)
        }

    suspend fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        val bookSource = GSON.fromJsonObject<BookSource>(postData).getOrNull()
        if (bookSource != null) {
            if (TextUtils.isEmpty(bookSource.bookSourceName) || TextUtils.isEmpty(bookSource.bookSourceUrl)) {
                returnData.setErrorMsg("源名称和URL不能为空")
            } else {
                withContext(IO) {
                    appDb.bookSourceDao.insert(bookSource)
                }
                returnData.setData("")
            }
        } else {
            returnData.setErrorMsg("转换源失败")
        }
        return returnData
    }

    suspend fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("数据为空")
        val okSources = arrayListOf<BookSource>()
        val bookSources = GSON.fromJsonArray<BookSource>(postData).getOrNull()
        if (bookSources.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("转换源失败")
        }
        withContext(IO) {
            bookSources.forEach { bookSource ->
                if (bookSource.bookSourceName.isNotBlank()
                    && bookSource.bookSourceUrl.isNotBlank()
                ) {
                    appDb.bookSourceDao.insert(bookSource)
                    okSources.add(bookSource)
                }
            }
        }
        return ReturnData().setData(okSources)
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定源地址")
        }
        val bookSource = appDb.bookSourceDao.getBookSource(url)
            ?: return returnData.setErrorMsg("未找到源，请检查书源地址")
        return returnData.setData(bookSource)
    }

    suspend fun deleteSources(postData: String?): ReturnData {
        val dataMap = GSON.fromJsonObject<Map<String, *>>(postData).getOrNull()
        val confirmed = dataMap?.get("confirmed") as? Boolean ?: false
        if (!confirmed) {
            return ReturnData().setErrorMsg("请确认删除操作，需传入 confirmed: true")
        }
        kotlin.runCatching {
            GSON.fromJsonArray<BookSource>(postData).getOrThrow().let {
                withContext(IO) {
                    SourceHelp.deleteBookSources(it)
                }
            }
        }.onFailure {
            return ReturnData().setErrorMsg(it.localizedMessage ?: "数据格式错误")
        }
        return ReturnData().setData("已执行"/*okSources*/)
    }
}
