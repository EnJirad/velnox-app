package com.velnox.core.common.paging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Paging is offset/limit because that is what the catalogue endpoint accepts. The
 * important rule is [Page.endReached]: it must be derived from the backend's own
 * signals, never from a client-side counter, so a genuinely empty last page still
 * reports "no more data" instead of looping forever.
 */
class PageTest {

    @Test
    fun `next advances by exactly one page`() {
        PageRequest(offset = 0, limit = 20).next shouldBe PageRequest(offset = 20, limit = 20)
        PageRequest(offset = 20, limit = 20).next shouldBe PageRequest(offset = 40, limit = 20)
    }

    @Test
    fun `limit is bounded by what the API accepts`() {
        // The backend caps `limit` at 200; asking for more is a client bug, not a
        // request the server should have to sanitise.
        shouldThrow<IllegalArgumentException> { PageRequest(limit = 0) }
        shouldThrow<IllegalArgumentException> { PageRequest(limit = 201) }
        shouldThrow<IllegalArgumentException> { PageRequest(offset = -1) }
    }

    @Test
    fun `a full page without other signals means there may be more`() {
        val page = Page(items = List(20) { it }, request = PageRequest(limit = 20))
        page.endReached shouldBe false
    }

    @Test
    fun `a short page means the end was reached`() {
        val page = Page(items = List(7) { it }, request = PageRequest(offset = 20, limit = 20))
        page.endReached shouldBe true
    }

    @Test
    fun `an empty page is the end, not an error`() {
        val page = Page(items = emptyList<Int>(), request = PageRequest(offset = 40, limit = 20))
        page.endReached shouldBe true
    }

    @Test
    fun `an explicit total decides the end`() {
        val page = Page(
            items = List(20) { it },
            request = PageRequest(offset = 0, limit = 20),
            total = 35,
        )
        page.endReached shouldBe false

        val lastPage = Page(
            items = List(15) { it },
            request = PageRequest(offset = 20, limit = 20),
            total = 35,
        )
        lastPage.endReached shouldBe true
    }

    @Test
    fun `an explicit hasMore wins over every derived signal`() {
        // The catalogue returns a bare array, so `hasMore` is the only signal a caller
        // can supply; when it is present it must be believed even if the page looks short.
        val page = Page(
            items = List(3) { it },
            request = PageRequest(limit = 20),
            hasMore = true,
        )
        page.endReached shouldBe false
    }

    @Test
    fun `mapping a page preserves its paging metadata`() {
        val page = Page(
            items = listOf(1, 2, 3),
            request = PageRequest(offset = 20, limit = 20),
            total = 100,
            hasMore = true,
        )

        val mapped = page.map { it * 2 }

        mapped.items shouldBe listOf(2, 4, 6)
        mapped.request shouldBe page.request
        mapped.total shouldBe 100
        mapped.hasMore shouldBe true
    }
}
