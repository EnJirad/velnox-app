package com.velnox.core.realtime

/**
 * Channels the Velnox WebSocket server accepts.
 *
 * Copied from `backend/realtime/index.ts` — the server rejects any `subscribe` that
 * is neither a public channel listed there nor the caller's own `user:{userId}`
 * channel. Subscribing to an unknown channel gets
 * `{ "type": "error", "code": "FORBIDDEN" }`, so the constants must stay in sync
 * with the backend rather than being guessed.
 */
object RealtimeChannels {
    const val CART_UPDATED = "cart:updated"
    const val ORDER_CREATED = "order:created"
    const val ORDER_UPDATED = "order:updated"
    const val PRODUCT_UPDATED = "product:updated"
    const val INVENTORY_UPDATED = "inventory:updated"
    const val SELLER_UPDATED = "seller:updated"
    const val AUDIT_CREATED = "audit:created"
    const val NOTIFICATION_CREATED = "notification:created"

    /** The only private channel a client may subscribe to — its own. */
    fun userChannel(userId: String) = "user:$userId"
}

/** Events the server pushes. Values match the `type` field of each frame. */
object RealtimeEvents {
    const val CONNECTED = "connected"
    const val SUBSCRIBED = "subscribed"
    const val UNSUBSCRIBED = "unsubscribed"
    const val ERROR = "error"

    const val CHAT_MESSAGE = "chat:message"
    const val CHAT_READ = "chat:read"
    const val NOTIFICATION_CREATED = "notification:created"
}

/**
 * A frame received from the server.
 *
 * The server's contract is `{ type, channel?, data?, timestamp? }`
 * (`broadcast()` in `backend/realtime/index.ts`). [payload] is kept as the raw JSON
 * string: each feature parses only the shape it needs, so a new backend field never
 * forces a client release, and a payload this build does not understand is ignored
 * instead of crashing the socket.
 */
data class RealtimeFrame(
    val type: String,
    val channel: String?,
    val payload: String?,
    val receivedAtEpochMillis: Long,
) {
    val isControlFrame: Boolean
        get() = type == RealtimeEvents.CONNECTED ||
            type == RealtimeEvents.SUBSCRIBED ||
            type == RealtimeEvents.UNSUBSCRIBED ||
            type == RealtimeEvents.ERROR
}

/** Socket lifecycle, surfaced so the UI can degrade honestly. */
sealed interface RealtimeConnectionState {
    data object Idle : RealtimeConnectionState
    data object Connecting : RealtimeConnectionState
    data class Connected(val userId: String?, val subscribedChannels: Set<String>) : RealtimeConnectionState
    /** Waiting to retry after [attempt] failures; [retryInMillis] until the next try. */
    data class Reconnecting(val attempt: Int, val retryInMillis: Long) : RealtimeConnectionState
    data object Paused : RealtimeConnectionState
}
