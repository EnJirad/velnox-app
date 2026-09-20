package com.velnox.core.realtime

import com.velnox.core.common.di.ApplicationScope
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.network.di.VelnoxApiOrigin
import com.velnox.core.network.di.VelnoxHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * The single Velnox WebSocket connection.
 *
 * ## What realtime is for here
 *
 * `docs/ai/REALTIME.md` is explicit: "WebSocket is a DELIVERY MECHANISM. Neon
 * PostgreSQL is the SOURCE OF TRUTH." So this client is a *hint*, and every
 * consumer reacts by re-reading the corresponding REST endpoint rather than trusting
 * the frame's contents as state. On every reconnect the app re-fetches, which is
 * what makes a dropped socket harmless.
 *
 * ## Handshake authentication
 *
 * The server authenticates the upgrade from the `velnox_session` cookie
 * (`resolveUserFromRequest` in `backend/realtime/index.ts`) and rejects revoked
 * tokens. The socket therefore reuses the authenticated OkHttp client, so the
 * cookie jar attached in `core:auth` supplies the credential — no token is passed in
 * a query string, where it would end up in proxy logs.
 *
 * ## Lifecycle
 *
 * A backgrounded app must not hold a socket: it burns battery and the server
 * sweeps revoked connections every 30 s anyway. [pause] closes the socket and
 * [resume] reconnects; `AppForegroundTracker` drives both. Consumers must re-fetch
 * on reconnect, which is exactly what this module's contract already requires.
 *
 * ## Reconnect policy
 *
 * Exponential backoff from 1 s to 15 s with jitter, matching
 * `packages/shared/src/lib/chat-socket.ts`, unbounded while the app is in the
 * foreground — backing off permanently would leave a user silently unsubscribed.
 */
@Singleton
class VelnoxRealtimeClient @Inject constructor(
    @VelnoxHttpClient okHttpClient: OkHttpClient,
    @VelnoxApiOrigin origin: HttpUrl,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    /**
     * Long-lived sockets must not be subject to a read timeout; a 20 s ping keeps
     * NAT and mobile-carrier idle timers from silently dropping the connection.
     */
    private val socketClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val socketUrl: HttpUrl = origin.newBuilder()
        .encodedPath(WS_PATH)
        .build()

    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Idle)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _frames = MutableSharedFlow<RealtimeFrame>(extraBufferCapacity = FRAME_BUFFER)
    /** Frames from the server. Control frames are filtered out. */
    val frames: SharedFlow<RealtimeFrame> = _frames.asSharedFlow()

    private val reconnectAttempts = AtomicInteger(0)

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var currentUserId: String? = null

    @Volatile
    private var paused: Boolean = false

    private val subscriptions = linkedSetOf<String>()

    /**
     * Connects and subscribes to the user's private channel.
     *
     * Safe to call repeatedly (each screen may call it on start): an already open
     * socket for the same user is left alone.
     */
    fun connect(userId: String) {
        if (userId.isBlank()) return
        currentUserId = userId

        if (socket != null) {
            // Same user, socket already up: just make sure the private channel is
            // subscribed (a reconnect may have happened while signed in).
            subscribe(RealtimeChannels.userChannel(userId))
            return
        }

        openSocket()
    }

    /** Explicit shutdown — used on sign-out. */
    fun disconnect() {
        paused = false
        currentUserId = null
        reconnectJob?.cancel()
        reconnectJob = null
        closeSocket()
        subscriptions.clear()
        _connectionState.value = RealtimeConnectionState.Idle
    }

    /** App moved to the background: close the socket, keep the intent to reconnect. */
    fun pause() {
        if (paused) return
        paused = true
        reconnectJob?.cancel()
        reconnectJob = null
        closeSocket()
        _connectionState.value = RealtimeConnectionState.Paused
    }

    /** App returned to the foreground: reconnect and re-subscribe. */
    fun resume() {
        if (!paused) return
        paused = false
        currentUserId?.let { openSocket() }
    }

    /** Subscribe to a channel. Queued until the socket is open. */
    fun subscribe(channel: String) {
        if (!isSubscribable(channel)) return
        synchronized(subscriptions) { subscriptions.add(channel) }
        // The private channel is implicit: connect() re-sends it on every open.
        if (channel.startsWith("user:")) return
        sendCommand(type = "subscribe", channel = channel)
    }

    fun unsubscribe(channel: String) {
        synchronized(subscriptions) { subscriptions.remove(channel) }
        sendCommand(type = "unsubscribe", channel = channel)
    }

    /**
     * Presence signal used by chat (`chat:viewing` / `chat:viewingEnd`).
     *
     * The server uses it only to suppress a notification for a thread the user is
     * already reading; the message itself still arrives over the socket.
     */
    fun sendChatViewing(conversationId: String?) {
        if (conversationId == null) {
            sendCommand(type = "chat:viewingEnd")
        } else {
            sendCommand(type = "chat:viewing", dataJson = """{"conversationId":"$conversationId"}""")
        }
    }

    private fun openSocket() {
        val userId = currentUserId ?: return
        if (paused) return
        closeSocket()

        _connectionState.value = RealtimeConnectionState.Connecting

        val request = Request.Builder()
            .url(socketUrl)
            // No Origin header: the upgrade is a GET, and origin-guard only inspects
            // state-changing methods, but leaving it off keeps native traffic
            // unambiguous.
            .build()

        socket = socketClient.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempts.set(0)
                    _connectionState.value = RealtimeConnectionState.Connected(userId, emptySet())
                    VelnoxLog.i(TAG) { "Realtime connected" }

                    // Re-subscribe everything: subscriptions do not survive a new
                    // socket, and dropping them silently would look like "no updates".
                    webSocket.send("""{"type":"subscribe","channel":"${RealtimeChannels.userChannel(userId)}"}""")
                    val toResubscribe = synchronized(subscriptions) { subscriptions.toList() }
                    for (channel in toResubscribe) {
                        if (channel.startsWith("user:")) continue
                        webSocket.send("""{"type":"subscribe","channel":"$channel"}""")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleFrame(text, userId)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    VelnoxLog.d(TAG) { "Realtime closed ($code)" }
                    socket = null
                    handleDisconnect(code)
                }

                override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                    VelnoxLog.w(TAG) { "Realtime failure: ${throwable::class.java.simpleName}" }
                    socket = null
                    handleDisconnect(code = response?.code ?: 0)
                }
            },
        )
    }

    private fun handleFrame(raw: String, userId: String) {
        val frame = RealtimeFrameParser.parse(raw) ?: run {
            VelnoxLog.d(TAG) { "Ignoring malformed realtime frame" }
            return
        }

        when (frame.type) {
            RealtimeEvents.SUBSCRIBED -> {
                frame.channel?.let { channel ->
                    val current = _connectionState.value
                    if (current is RealtimeConnectionState.Connected) {
                        _connectionState.value = current.copy(
                            subscribedChannels = current.subscribedChannels + channel,
                        )
                    }
                }
            }

            RealtimeEvents.ERROR -> VelnoxLog.w(TAG) { "Server refused a realtime subscription" }

            else -> {
                if (!frame.isControlFrame) {
                    // extraBufferCapacity + tryEmit keeps a burst from suspending the
                    // socket reader; a dropped hint is harmless because consumers
                    // re-fetch from the API anyway.
                    _frames.tryEmit(frame)
                }
            }
        }

        // Keep the private channel honest if the server restarted the session.
        if (frame.type == RealtimeEvents.CONNECTED) {
            subscribe(RealtimeChannels.userChannel(userId))
        }
    }

    /**
     * `4001` is the server's "Session revoked" close code — a logout elsewhere, or
     * an admin action. Reconnecting with the same token would loop forever, so the
     * client stops and lets the 401 path in `core:auth` sign the user out.
     */
    private fun handleDisconnect(code: Int) {
        if (code == CLOSE_SESSION_REVOKED) {
            VelnoxLog.w(TAG) { "Session revoked — stopping realtime" }
            currentUserId = null
            _connectionState.value = RealtimeConnectionState.Idle
            return
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (paused || currentUserId == null) return
        if (reconnectJob?.isActive == true) return

        val attempt = reconnectAttempts.getAndIncrement()
        val delayMillis = backoffMillis(attempt)

        _connectionState.value = RealtimeConnectionState.Reconnecting(attempt + 1, delayMillis)

        reconnectJob = applicationScope.launch {
            delay(delayMillis)
            openSocket()
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        val exponential = min(BASE_BACKOFF_MS shl min(attempt, 10), MAX_BACKOFF_MS)
        val jitter = Random.nextLong(0, BASE_BACKOFF_MS)
        return min(exponential + jitter, MAX_BACKOFF_MS)
    }

    private fun sendCommand(type: String, channel: String? = null, dataJson: String? = null) {
        val socket = socket ?: return
        val frame = buildString {
            append("""{"type":"""").append(type).append('"')
            channel?.let { append(""","channel":"""").append(it).append('"') }
            dataJson?.let { append(""","data":""").append(it) }
            append('}')
        }
        socket.send(frame)
    }

    private fun closeSocket() {
        socket?.let { current ->
            runCatching { current.close(NORMAL_CLOSURE, "client closing") }
        }
        socket = null
    }

    /** Mirrors the server's own allowlist, plus the caller's private channel. */
    private fun isSubscribable(channel: String): Boolean = when {
        channel.startsWith("user:") -> currentUserId != null && channel == RealtimeChannels.userChannel(currentUserId!!)
        else -> channel in PUBLIC_CHANNELS
    }

    private companion object {
        const val TAG = "VelnoxRealtime"
        const val WS_PATH = "/ws"
        const val FRAME_BUFFER = 64
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 15_000L
        const val NORMAL_CLOSURE = 1000

        /** `ws.close(4001, "Session revoked")` in backend/realtime/index.ts. */
        const val CLOSE_SESSION_REVOKED = 4001

        val PUBLIC_CHANNELS = setOf(
            RealtimeChannels.CART_UPDATED,
            RealtimeChannels.ORDER_CREATED,
            RealtimeChannels.ORDER_UPDATED,
            RealtimeChannels.PRODUCT_UPDATED,
            RealtimeChannels.INVENTORY_UPDATED,
            RealtimeChannels.SELLER_UPDATED,
            RealtimeChannels.AUDIT_CREATED,
            RealtimeChannels.NOTIFICATION_CREATED,
        )
    }
}
