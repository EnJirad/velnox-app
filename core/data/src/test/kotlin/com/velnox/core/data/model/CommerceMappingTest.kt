package com.velnox.core.data.model

import com.velnox.core.common.domain.OrderStatus
import com.velnox.core.common.domain.PaymentStatus
import com.velnox.core.data.dto.AddressDto
import com.velnox.core.data.dto.CartItemDto
import com.velnox.core.data.dto.CustomerProfileDto
import com.velnox.core.data.dto.OrderDto
import com.velnox.core.data.dto.OrderItemDto
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Cart and order mapping.
 *
 * Everything here is money the customer is asked to confirm, so the rules that matter are
 * the ones that could quietly show a wrong figure: which price a line uses, what an order
 * totals to, and what a missing item count falls back to.
 */
class CommerceMappingTest {

    private fun cartItem(
        id: String = "ci1",
        quantity: Int = 2,
        price: Double = 100.0,
        availableStock: Int = 10,
    ) = CartItemDto(
        id = id,
        productId = "p1",
        productName = "หูฟัง",
        quantity = quantity,
        priceSnapshot = price,
        availableStock = availableStock,
    )

    private fun orderDto(
        itemCount: Int = 0,
        items: List<OrderItemDto> = emptyList(),
        status: String = "pending",
    ) = OrderDto(
        id = "o1",
        orderNumber = "ORD-1001",
        status = status,
        paymentStatus = "unpaid",
        subtotal = 200.0,
        shippingFee = 50.0,
        total = 250.0,
        itemCount = itemCount,
        items = items,
    )

    @Test
    fun `cart line totals use the price snapshot the backend captured`() {
        val line = cartItem(quantity = 3, price = 33.5).toDomain()

        line.lineTotal shouldBe 100.5
        line.unitPriceLabel shouldContain "฿"
        line.lineTotalLabel shouldContain "฿"
    }

    @Test
    fun `cart totals are the sum of the lines, and the count is the sum of quantities`() {
        val cart = Cart(
            listOf(
                cartItem(id = "a", quantity = 2, price = 100.0).toDomain(),
                cartItem(id = "b", quantity = 3, price = 50.0).toDomain(),
            ),
        )

        cart.itemCount shouldBe 5
        cart.subtotal shouldBe 350.0
        cart.isEmpty shouldBe false
        Cart.Empty.isEmpty shouldBe true
    }

    @Test
    fun `quantity is clamped to the live stock the backend reported`() {
        val line = cartItem(quantity = 2, availableStock = 2).toDomain()

        // The cart refuses to grow past the live variant stock...
        line.canIncrease shouldBe false
        // ...and never drops below one, so a mis-tap on minus cannot empty a line.
        line.canDecrease shouldBe true
        cartItem(quantity = 1, availableStock = 5).toDomain().canDecrease shouldBe false
    }

    @Test
    fun `a line the backend reports as unsatisfiable is flagged`() {
        cartItem(availableStock = 0).toDomain().isOutOfStock shouldBe true
        cartItem(availableStock = 4).toDomain().isOutOfStock shouldBe false
    }

    @Test
    fun `variant option labels render from either primitive or object arrays`() {
        val primitives = cartItem().copy(
            variantOptionLabels = JsonArray(listOf(JsonPrimitive("ดำ"), JsonPrimitive("M"))),
        ).toDomain()
        primitives.variantLabel shouldBe "ดำ / M"

        // The backend's exact shape is not documented, so an array of objects with a
        // label-ish field must also render rather than producing an empty string.
        val objects = cartItem().copy(
            variantOptionLabels = JsonArray(
                listOf(
                    JsonObject(mapOf("label" to JsonPrimitive("น้ำเงิน"))),
                    JsonObject(mapOf("valueLabel" to JsonPrimitive("XL"))),
                ),
            ),
        ).toDomain()
        objects.variantLabel shouldBe "น้ำเงิน / XL"
    }

    @Test
    fun `a named variant wins over the option label array`() {
        val line = cartItem().copy(
            variantName = "แดง / L",
            variantOptionLabels = JsonArray(listOf(JsonPrimitive("ดำ"))),
        ).toDomain()

        line.variantLabel shouldBe "แดง / L"
    }

    @Test
    fun `order item count falls back to the real line quantities`() {
        // The list endpoint can omit `itemCount`; showing "0 items" next to a three-line
        // order is the bug this guards against.
        val order = orderDto(
            itemCount = 0,
            items = listOf(
                OrderItemDto(id = "i1", productName = "A", quantity = 2, subtotal = 100.0),
                OrderItemDto(id = "i2", productName = "B", quantity = 1, subtotal = 100.0),
            ),
        ).toDomain()

        order.itemCount shouldBe 3
    }

    @Test
    fun `an explicit order item count is trusted`() {
        val order = orderDto(
            itemCount = 7,
            items = listOf(OrderItemDto(id = "i1", productName = "A", quantity = 2)),
        ).toDomain()

        order.itemCount shouldBe 7
    }

    @Test
    fun `order status and payment status are parsed, not copied`() {
        val order = orderDto(status = "shipped").toDomain()

        order.status shouldBe OrderStatus.Shipped
        order.paymentStatus shouldBe PaymentStatus.Unpaid
        order.shortNumber shouldBe "1001"
        order.totalLabel shouldContain "฿"
    }

    @Test
    fun `an order is cancellable only before it ships`() {
        orderDto(status = "pending").toDomain().isCancellable shouldBe true
        orderDto(status = "confirmed").toDomain().isCancellable shouldBe true
        orderDto(status = "shipped").toDomain().isCancellable shouldBe false
        orderDto(status = "delivered").toDomain().isCancellable shouldBe false
        orderDto(status = "completed").toDomain().isCancellable shouldBe false
        orderDto(status = "cancelled").toDomain().isCancellable shouldBe false
    }

    @Test
    fun `blank optional fields become null instead of empty strings`() {
        val order = orderDto().copy(note = "   ", shopName = "", trackingNumber = "").toDomain()

        order.note shouldBe null
        order.shopName shouldBe null
        order.trackingNumber shouldBe null
    }

    @Test
    fun `an address renders as one line, skipping the parts that are missing`() {
        val address = AddressDto(
            id = "a1",
            recipientName = "สมชาย",
            phone = "0812345678",
            line1 = "123 ถนนสุขุมวิท",
            subdistrict = "คลองเตย",
            city = "กรุงเทพ",
            postalCode = "10110",
        ).toDomain()

        address.formatted shouldBe "123 ถนนสุขุมวิท, คลองเตย, กรุงเทพ, 10110"
    }

    @Test
    fun `a customer profile name is built from whatever parts exist`() {
        CustomerProfileDto(firstName = "สมชาย", lastName = null).toDomain().fullName shouldBe "สมชาย"
        CustomerProfileDto(firstName = null, lastName = "ใจดี").toDomain().fullName shouldBe "ใจดี"
        CustomerProfileDto().toDomain().fullName shouldBe ""
    }
}
