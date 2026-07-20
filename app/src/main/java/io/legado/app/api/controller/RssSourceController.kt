package io.legado.app.api.controller


import android.text.TextUtils
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

object RssSourceController {

    val sources: ReturnData
        get() {
            val source = appDb.rssSourceDao.all
            val returnData = ReturnData()
            return if (source.isEmpty()) {
                returnData.setErrorMsg("源列表为空")
            } else returnData.setData(source)
        }

    suspend fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        GSON.fromJsonObject<RssSource>(postData).onFailure {
            returnData.setErrorMsg("转换源失败${it.localizedMessage}")
        }.onSuccess { source ->
            if (TextUtils.isEmpty(source.sourceName) || TextUtils.isEmpty(source.sourceUrl)) {
                returnData.setErrorMsg("源名称和URL不能为空")
            } else {
                withContext(IO) {
                    appDb.rssSourceDao.insert(source)
                }
                returnData.setData("")
            }
        }
        return returnData
    }

    suspend fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("数据不能为空")
        val okSources = arrayListOf<RssSource>()
        val source = GSON.fromJsonArray<RssSource>(postData).getOrNull()
        if (source.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("转换源失败")
        }
        withContext(IO) {
            for (rssSource in source) {
                if (rssSource.sourceName.isBlank() || rssSource.sourceUrl.isBlank()) {
                    continue
                }
                appDb.rssSourceDao.insert(rssSource)
                okSources.add(rssSource)
            }
        }
        return ReturnData().setData(okSources)
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书源地址")
        }
        val source = appDb.rssSourceDao.getByKey(url)
            ?: return returnData.setErrorMsg("未找到源，请检查源地址")
        return returnData.setData(source)
    }

    suspend fun deleteSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("没有传递数据")
        val dataMap = GSON.fromJsonObject<Map<String, *>>(postData).getOrNull()
        val confirmed = dataMap?.get("confirmed") as? Boolean ?: false
        if (!confirmed) {
            return ReturnData().setErrorMsg("请确认删除操作，需传入 confirmed: true")
        }
        GSON.fromJsonArray<RssSource>(postData).onFailure {
            return ReturnData().setErrorMsg("格式不对")
        }.onSuccess {
            withContext(IO) {
                SourceHelp.deleteRssSources(it)
            }
        }
        return ReturnData().setData("已执行"/*okSources*/)
    }
}
