package gay.doughmination.plural.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.random.Random

/**
 * The API's realtime socket (/v2/ws), the website's live.svelte.ts in Kotlin:
 * fronter and mental-state changes, and an admin's "refresh everyone". Open
 * only while the app is on screen. Pings every 30s and gives up on a socket
 * that doesn't answer within 10s; reconnects with jittered exponential backoff.
 */
class Live(private val api: Api, private val scope: CoroutineScope) {
    enum class Status { Connecting, Open, Reconnecting, Closed }

    enum class Event { FrontersChanged, MentalStateChanged, ForceRefresh, Reconnected }

    private val _status = MutableStateFlow(Status.Closed)
    val status: StateFlow<Status> = _status

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events

    private var socket: WebSocket? = null
    private var pinger: Job? = null
    private var pongTimeout: Job? = null
    private var retry: Job? = null
    private var running = false
    private var attempts = 0

    /** Whether a socket has opened before: every later open may have missed events. */
    private var opened = false

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        retry?.cancel()
        pinger?.cancel()
        pongTimeout?.cancel()
        socket?.close(1000, null)
        socket = null
        _status.value = Status.Closed
    }

    private fun connect() {
        if (!running || socket != null) return
        _status.value = if (attempts > 0) Status.Reconnecting else Status.Connecting
        val request = Request.Builder().url(api.socketUrl).build()
        socket = api.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (socket !== webSocket) return@launch
                    // Anything that changed while we were away (backgrounded, or dropped) gets picked up.
                    if (opened) _events.tryEmit(Event.Reconnected)
                    opened = true
                    attempts = 0
                    _status.value = Status.Open
                    pinger?.cancel()
                    pinger = scope.launch {
                        while (true) {
                            delay(PING_MS)
                            webSocket.send("ping")
                            if (pongTimeout == null) {
                                pongTimeout = scope.launch {
                                    delay(PONG_TIMEOUT_MS)
                                    webSocket.cancel()
                                }
                            }
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong") {
                    scope.launch {
                        pongTimeout?.cancel()
                        pongTimeout = null
                    }
                    return
                }
                when (api.eventType(text)) {
                    "fronters_update" -> _events.tryEmit(Event.FrontersChanged)
                    "mental_state_update" -> _events.tryEmit(Event.MentalStateChanged)
                    "force_refresh" -> _events.tryEmit(Event.ForceRefresh)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
        })
    }

    private fun lost(webSocket: WebSocket) {
        scope.launch {
            if (socket !== webSocket) return@launch
            pinger?.cancel()
            pongTimeout?.cancel()
            pongTimeout = null
            socket = null
            if (!running) return@launch
            _status.value = Status.Reconnecting
            val ceiling = minOf(1000L shl attempts.coerceAtMost(5), MAX_BACKOFF_MS)
            attempts += 1
            retry = scope.launch {
                delay((Random.nextDouble() * ceiling).toLong())
                connect()
            }
        }
    }

    private companion object {
        const val PING_MS = 30_000L
        const val PONG_TIMEOUT_MS = 10_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
