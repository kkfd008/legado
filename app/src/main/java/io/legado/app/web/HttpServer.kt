package io.legado.app.web

import android.graphics.Bitmap
import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookController
import io.legado.app.api.controller.BookSourceController
import io.legado.app.api.controller.ReplaceRuleController
import io.legado.app.api.controller.RssSourceController
import io.legado.app.api.controller.TxtTocRuleController
import io.legado.app.help.config.AppConfig
import io.legado.app.service.WebService
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.stackTraceStr
import io.legado.app.web.utils.AssetsWeb
import kotlinx.coroutines.runBlocking
import okio.Pipe
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

class HttpServer(port: Int) : NanoHTTPD(port) {
    private val assetsWeb = AssetsWeb("web")

    /**
     * 验证 HTTP Basic 认证。当用户设置了 webPassword 时才开启认证。
     * 返回 true 表示通过认证，false 表示需要返回 401。
     */
    private fun authenticate(session: IHTTPSession): Boolean {
        val password = AppConfig.webPassword
        // 未设置密码时不强制认证，保持向后兼容
        if (password.isBlank()) return true

        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        if (authHeader.isNullOrBlank()) return false

        // 支持 Basic 和 Bearer 两种方式
        return kotlin.runCatching {
            when {
                authHeader.startsWith("Basic ", ignoreCase = true) -> {
                    val decoded = String(Base64.getDecoder().decode(authHeader.substring(6).trim()))
                    val expected = "legado:$password"
                    decoded == expected || decoded == password
                }
                authHeader.startsWith("Bearer ", ignoreCase = true) -> {
                    authHeader.substring(7).trim() == password
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun unauthorized(): Response {
        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized")
            .also {
                it.addHeader("WWW-Authenticate", "Basic realm=\"Legado\", charset=\"UTF-8\"")
            }
    }

    override fun serve(session: IHTTPSession): Response {
        if (!WebService.isRun) {
            WebService.serve()
        }
        var returnData: ReturnData? = null
        val ct = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers["content-type"] = ct.contentTypeHeader
        var uri = session.uri

        val startAt = System.currentTimeMillis()
        LogUtils.d(TAG) {
            "${session.method.name} - $uri - ${session.queryParameterString} - Start($startAt)"
        }

        try {
            // 认证检查：静态资源和 API 都需要认证
            if (!authenticate(session)) {
                return unauthorized()
            }

            when (session.method) {
                Method.OPTIONS -> {
                    val response = newFixedLengthResponse("")
                    response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    response.addHeader("Access-Control-Allow-Headers", "content-type, authorization")
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    response.addHeader("Access-Control-Allow-Credentials", "false")
                    response.addHeader("Access-Control-Max-Age", "3600")
                    return response
                }

                Method.POST -> {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val postData = files["postData"] ?: session.parms["postData"]

                    returnData = runBlocking {
                        when (uri) {
                            "/saveBookSource" -> BookSourceController.saveSource(postData)
                            "/saveBookSources" -> BookSourceController.saveSources(postData)
                            "/deleteBookSources" -> BookSourceController.deleteSources(postData)
                            "/saveBook" -> BookController.saveBook(postData)
                            "/deleteBook" -> BookController.deleteBook(postData)
                            "/saveBookProgress" -> BookController.saveBookProgress(postData)
                            "/addLocalBook" -> BookController.addLocalBook(session.parameters, files)
                            "/saveReadConfig" -> BookController.saveWebReadConfig(postData)
                            "/saveRssSource" -> RssSourceController.saveSource(postData)
                            "/saveRssSources" -> RssSourceController.saveSources(postData)
                            "/deleteRssSources" -> RssSourceController.deleteSources(postData)
                            "/saveReplaceRule" -> ReplaceRuleController.saveRule(postData)
                            "/deleteReplaceRule" -> ReplaceRuleController.delete(postData)
                            "/testReplaceRule" -> ReplaceRuleController.testRule(postData)
                            "/saveTxtTocRule" -> TxtTocRuleController.saveRule(postData)
                            "/deleteTxtTocRule" -> TxtTocRuleController.delete(postData)
                            else -> null
                        }
                    }
                }

                Method.GET -> {
                    val parameters = session.parameters

                    returnData = when (uri) {
                        "/getBookSource" -> BookSourceController.getSource(parameters)
                        "/getBookSources" -> BookSourceController.sources
                        "/getBookshelf" -> BookController.bookshelf
                        "/getChapterList" -> BookController.getChapterList(parameters)
                        "/refreshToc" -> BookController.refreshToc(parameters)
                        "/getBookContent" -> BookController.getBookContent(parameters)
                        "/cover" -> BookController.getCover(parameters)
                        "/image" -> BookController.getImg(parameters)
                        "/getReadConfig" -> BookController.getWebReadConfig()
                        "/getRssSource" -> RssSourceController.getSource(parameters)
                        "/getRssSources" -> RssSourceController.sources
                        "/getReplaceRules" -> ReplaceRuleController.allRules
                        "/getTxtTocRules" -> TxtTocRuleController.allRules
                        else -> null
                    }
                }

                else -> Unit
            }

            if (returnData == null) {
                if (uri.endsWith("/"))
                    uri += "index.html"
                return assetsWeb.getResponse(uri)
            }

            val response = if (returnData.data is Bitmap) {
                val outputStream = ByteArrayOutputStream()
                (returnData.data as Bitmap).compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                outputStream.close()
                val inputStream = ByteArrayInputStream(byteArray)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/png",
                    inputStream,
                    byteArray.size.toLong()
                )
            } else {
                val data = returnData.data
                if (data is List<*> && data.size > 3000) {
                    val pipe = Pipe(16 * 1024)
                    io.legado.app.help.coroutine.Coroutine.async {
                        pipe.sink.buffer().outputStream().bufferedWriter(Charsets.UTF_8).use {
                            GSON.toJson(returnData, it)
                        }
                    }
                    newChunkedResponse(
                        Response.Status.OK,
                        "application/json",
                        pipe.source.buffer().inputStream()
                    )
                } else {
                    newFixedLengthResponse(GSON.toJson(returnData))
                }
            }
            response.addHeader("Access-Control-Allow-Methods", "GET, POST")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Credentials", "false")
            response.addHeader("X-Content-Type-Options", "nosniff")
            response.addHeader("X-Frame-Options", "DENY")
            response.addHeader("Referrer-Policy", "no-referrer")
            LogUtils.d(TAG) {
                "${session.method.name} - $uri - ${session.queryParameterString} - End($startAt)"
            }
            return response
        } catch (e: Exception) {
            LogUtils.d(TAG) {
                "${session.method.name} - $uri - ${session.queryParameterString} - Error End($startAt)\n$e\n${e.stackTraceStr}"
            }
            return newFixedLengthResponse(e.message)
        }

    }

    companion object {
        private const val TAG = "HttpServer"
    }

}
