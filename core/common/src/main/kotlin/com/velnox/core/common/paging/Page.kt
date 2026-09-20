package com.velnox.core.common.paging

/**
 * Offset/limit pagination, matching what the Velnox backend actually accepts.
 *
 * The catalogue uses `offset` + `limit` (`GET /api/products/catalog`); orders and
 * notifications use `limit` only. There is no cursor API, so this is the whole
 * pagination contract.
 */
data class PageRequest(
    val offset: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
) {
    val next: PageRequest get() = copy(offset = offset + limit)

    init {
        require(limit in 1..MAX_LIMIT) { "limit must be 1..$MAX_LIMIT, was $limit" }
        require(offset >= 0) { "offset must not be negative, was $offset" }
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 200
    }
}

/**
 * One page plus the knowledge needed to continue.
 *
 * [endReached] is derived from the backend's own signals only (a short page, or
 * an explicit `total`/`hasMore` when present) — never guessed from a client-side
 * counter, so a page that is genuinely empty still reports "no more data".
 */
data class Page<T>(
    val items: List<T>,
    val request: PageRequest,
    val total: Int? = null,
    val hasMore: Boolean? = null,
) {
    val endReached: Boolean
        get() = when {
            hasMore != null -> !hasMore
            total != null -> request.offset + items.size >= total
            else -> items.size < request.limit
        }

    fun <R> map(transform: (T) -> R): Page<R> =
        Page(items.map(transform), request, total, hasMore)
}

/** Append a freshly loaded page onto the accumulated list. */
fun <T> List<T>.appendPage(page: Page<T>): List<T> = this + page.items
