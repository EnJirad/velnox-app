package com.velnox.core.common.domain

import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The shared status vocabulary is the contract between the backend's wire values and
 * every screen in all three apps. These tests pin the two properties that matter:
 *
 *  * an unknown value from a newer backend is never silently mapped onto a known status;
 *  * the order state machine offers exactly the transitions the backend enforces.
 */
class CommerceStatusTest {

    @Test
    fun `order status parses the backend wire values`() {
        OrderStatus.fromWire("pending") shouldBe OrderStatus.Pending
        OrderStatus.fromWire("confirmed") shouldBe OrderStatus.Confirmed
        OrderStatus.fromWire("shipped") shouldBe OrderStatus.Shipped
        OrderStatus.fromWire("delivered") shouldBe OrderStatus.Delivered
        OrderStatus.fromWire("completed") shouldBe OrderStatus.Completed
        OrderStatus.fromWire("cancelled") shouldBe OrderStatus.Cancelled
    }

    @Test
    fun `order status is case insensitive and defaults to unknown`() {
        OrderStatus.fromWire("PENDING") shouldBe OrderStatus.Pending
        OrderStatus.fromWire("refunded") shouldBe OrderStatus.Unknown
        OrderStatus.fromWire(null) shouldBe OrderStatus.Unknown
        OrderStatus.fromWire("") shouldBe OrderStatus.Unknown
    }

    @Test
    fun `pending order can be confirmed or cancelled`() {
        OrderStatus.Pending.allowedNextStatuses shouldBe listOf(
            OrderStatus.Confirmed,
            OrderStatus.Cancelled,
        )
    }

    @Test
    fun `fulfilment is strictly forward once shipped`() {
        OrderStatus.Confirmed.allowedNextStatuses shouldBe listOf(
            OrderStatus.Shipped,
            OrderStatus.Cancelled,
        )
        // Once shipped, cancellation is no longer offered — matching the backend, where
        // a shipped order cannot be cancelled by the customer.
        OrderStatus.Shipped.allowedNextStatuses shouldBe listOf(OrderStatus.Delivered)
        OrderStatus.Delivered.allowedNextStatuses shouldBe listOf(OrderStatus.Completed)
    }

    @Test
    fun `terminal and unknown statuses offer no transitions`() {
        OrderStatus.Completed.allowedNextStatuses shouldBe emptyList()
        OrderStatus.Cancelled.allowedNextStatuses shouldBe emptyList()
        OrderStatus.Unknown.allowedNextStatuses shouldBe emptyList()
    }

    @Test
    fun `product is purchasable only when published`() {
        ProductStatus.Published.isPurchasable shouldBe true
        ProductStatus.Draft.isPurchasable shouldBe false
        ProductStatus.PendingReview.isPurchasable shouldBe false
        ProductStatus.Rejected.isPurchasable shouldBe false
        ProductStatus.Suspended.isPurchasable shouldBe false
        ProductStatus.Archived.isPurchasable shouldBe false
        ProductStatus.Unknown.isPurchasable shouldBe false
    }

    @Test
    fun `only pending review occupies a moderation slot`() {
        ProductStatus.PendingReview.isAwaitingModeration shouldBe true
        ProductStatus.Draft.isAwaitingModeration shouldBe false
        ProductStatus.Published.isAwaitingModeration shouldBe false
    }

    @Test
    fun `only an approved seller gets the workspace`() {
        SellerStatus.Approved.isApproved shouldBe true
        SellerStatus.Pending.isApproved shouldBe false
        SellerStatus.UnderReview.isApproved shouldBe false
        SellerStatus.NeedsCorrection.isApproved shouldBe false
        SellerStatus.Rejected.isApproved shouldBe false
        SellerStatus.Suspended.isApproved shouldBe false
        SellerStatus.Unknown.isApproved shouldBe false
    }

    @Test
    fun `reapplication is offered only where the seller lifecycle allows it`() {
        SellerStatus.Rejected.canReapply shouldBe true
        SellerStatus.NeedsCorrection.canReapply shouldBe true
        SellerStatus.Unknown.canReapply shouldBe true

        // A suspended shop is a decision, not a mistake to correct.
        SellerStatus.Suspended.canReapply shouldBe false
        SellerStatus.Pending.canReapply shouldBe false
        SellerStatus.UnderReview.canReapply shouldBe false
        SellerStatus.Approved.canReapply shouldBe false
    }

    @Test
    fun `the V badge is driven only by a verified status`() {
        VerificationStatus.Verified.isVerified shouldBe true
        VerificationStatus.Pending.isVerified shouldBe false
        VerificationStatus.Unverified.isVerified shouldBe false
        VerificationStatus.Rejected.isVerified shouldBe false
        VerificationStatus.Suspended.isVerified shouldBe false
        VerificationStatus.Unknown.isVerified shouldBe false
    }

    @Test
    fun `payment and product statuses keep their wire values`() {
        PaymentStatus.fromWire("partially_refunded") shouldBe PaymentStatus.PartiallyRefunded
        PaymentStatus.fromWire("paid") shouldBe PaymentStatus.Paid
        ProductStatus.fromWire("pending_review") shouldBe ProductStatus.PendingReview
        SellerStatus.fromWire("under_review") shouldBe SellerStatus.UnderReview
        VerificationStatus.fromWire("verified") shouldBe VerificationStatus.Verified
    }

    @Test
    fun `every status carries the exact wire value the backend sends`() {
        OrderStatus.Pending.wireValue shouldBe "pending"
        PaymentStatus.PartiallyRefunded.wireValue shouldBe "partially_refunded"
        ProductStatus.PendingReview.wireValue shouldBe "pending_review"
        SellerStatus.NeedsCorrection.wireValue shouldBe "needs_correction"
        VerificationStatus.Unverified.wireValue shouldBe "unverified"
    }

    @Test
    fun `catalogue sort options match the values the API accepts`() {
        CatalogSort.Newest.wireValue shouldBe "newest"
        CatalogSort.PriceAsc.wireValue shouldBe "price_asc"
        CatalogSort.PriceDesc.wireValue shouldBe "price_desc"
        CatalogSort.Popular.wireValue shouldBe "popular"
        CatalogSort.Rating.wireValue shouldBe "rating"
    }
}
