package gay.doughmination.mail.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Keeps a WebSocket to /api/live open while the app is in the foreground and
 * emits when new mail lands. Reconnects with backoff; a reconnect emits too,
 * so anything missed while it was down gets picked up.
 */
class Live(private val api: Api, private val scope: CoroutineScope) {
    /** The thread new mail landed in, or null for "something may have changed". */
    private val _events = MutableSharedFlow<String?>(extraBufferCapacity = 16)
    val events: SharedFlow<String?> = _events

    private var socket: WebSocket? = null
    private var pinger: Job? = null
    private var retry: Job? = null
    private var running = false
    private var backoffMs = 1_000L
    private var dropped = false

    @Serializable
    private data class Event(val type: String, val threadId: String? = null)

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        retry?.cancel()
        pinger?.cancel()
        socket?.close(1000, null)
        socket = null
        dropped = true
    }

    private fun connect() {
        if (!running || socket != null) return
        val url = api.baseUrl.replaceFirst("http", "ws") + "/api/live"
        val request = api.authorize(Request.Builder().url(url)).build()
        socket = api.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (socket !== webSocket) return@launch
                    backoffMs = 1_000L
                    // Cloudflare drops idle sockets after ~100s; the server answers without waking.
                    pinger = scope.launch {
                        while (true) {
                            delay(30_000)
                            webSocket.send("ping")
                        }
                    }
                    if (dropped) _events.tryEmit(null)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong") return
                val event = runCatching { api.json.decodeFromString<Event>(text) }.getOrNull() ?: return
                if (event.type == "mail") _events.tryEmit(event.threadId)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
        })
    }

    private fun lost(webSocket: WebSocket) {
        scope.launch {
            if (socket !== webSocket) return@launch
            pinger?.cancel()
            socket = null
            dropped = true
            if (!running) return@launch
            retry = scope.launch {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
                connect()
            }
        }
    }
}
