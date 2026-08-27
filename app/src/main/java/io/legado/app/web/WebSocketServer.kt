package io.legado.app.web

import fi.iki.elonen.NanoWSD
import io.legado.app.help.config.AppConfig
import io.legado.app.service.WebService
import io.legado.app.web.socket.*
import java.util.Base64

class WebSocketServer(port: Int) : NanoWSD(port) {

    /**
     * 验证 WebSocket 连接认证。
     * 支持两种方式：query 带 token 参数，或 Sec-WebSocket-Protocol 带 token。
     * 当用户未设置密码时不强制认证。
     */
    private fun authenticate(handshake: IHTTPSession): Boolean {
        val password = AppConfig.webPassword
        if (password.isBlank()) return true

        // 方式1: query parameter ?token=xxx
        handshake.parameters["token"]?.let { token ->
            if (token == password) return true
        }

        // 方式2: Authorization header (Basic/Bearer)
        val authHeader = handshake.headers["authorization"] ?: handshake.headers["Authorization"]
        authHeader?.let { header ->
            kotlin.runCatching {
                when {
                    header.startsWith("Basic ", ignoreCase = true) -> {
                        val decoded = String(Base64.getDecoder().decode(header.substring(6).trim()))
                        val expected = "legado:$password"
                        decoded == expected || decoded == password
                    }
                    header.startsWith("Bearer ", ignoreCase = true) -> {
                        header.substring(7).trim() == password
                    }
                    else -> false
                }
            }.getOrDefault(false).let { if (it) return true }
        }

        return false
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        WebService.serve()

        // 对所有 WebSocket endpoint 进行认证
        if (!authenticate(handshake)) {
            return null
        }

        return when (handshake.uri) {
            "/bookSourceDebug" -> {
                BookSourceDebugWebSocket(handshake)
            }
            "/rssSourceDebug" -> {
                RssSourceDebugWebSocket(handshake)
            }
            "/searchBook" -> {
                BookSearchWebSocket(handshake)
            }
            else -> null
        }
    }
}
