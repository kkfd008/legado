package io.legado.app.web

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.app.constant.PreferKey
import io.legado.app.service.WebService
import io.legado.app.utils.getPrefString
import io.legado.app.web.socket.*
import splitties.init.appCtx

class WebSocketServer(port: Int) : NanoWSD(port) {

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): WebSocket? {
        val authToken = appCtx.getPrefString(PreferKey.webServiceAuthToken)
        if (authToken.isNotBlank()) {
            val requestToken = handshake.headers["Authorization"]
                ?: handshake.parameters["token"]?.firstOrNull()
            if (requestToken != "Bearer $authToken" && requestToken != authToken) {
                return null
            }
        }

        WebService.serve()
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
