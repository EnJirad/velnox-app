package com.velnox.core.data.model

import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.domain.VerificationStatus
import com.velnox.core.data.dto.CatalogQuery
import com.velnox.core.data.dto.CategoryDto
import com.velnox.core.data.dto.InventoryDto
import com.velnox.core.data.dto.ProductDto
import com.velnox.core.data.dto.ProductImageDto
import com.velnox.core.data.dto.ProductVariantDto
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Catalogue mapping.
 *
 * Two rules carry real risk and are covered here: which stock figure a product reports
 * (the one the cart and the buy button depend on), and which query parameters the
 * catalogue request actually sends — an empty `category` would filter the catalogue to
 * nothing on the backend.
 */
class CatalogMappingTest {

    private fun product(
        status: String = "published",
        inventory: InventoryDto? = null,
        variants: List<ProductVariantDto> = emptyList(),
        images: List<ProductImageDto> = emptyList(),
        primaryImage: ProductImageDto? = null,
        unit: String? = "ชิ้น",
        compareAtPrice: Double? = null,
        categorySlug: String? = "audio",
    ) = ProductDto(
        id = "p1",
        name = "หูฟัง",
        price = 100.0,
        compareAtPrice = compareAtPrice,
        status = status,
        unit = unit,
        categorySlug = categorySlug,
        sellerVerificationStatus = "verified",
        inventory = inventory,
        variants = variants,
        images = images,
        primaryImage = primaryImage,
    )

    @Test
    fun `inventory is preferred over the sum of variant stock`() {
        val mapped = product(
            inventory = InventoryDto(quantity = 10, available = 4, reorderLevel = 2),
            variants = listOf(ProductVariantDto(id = "v1", stock = 99)),
        ).toDomain()

        mapped.availableStock shouldBe 4
        mapped.reorderLevel shouldBe 2
    }

    @Test
    fun `variant stock is used only when there is no inventory row`() {
        val mapped = product(
            variants = listOf(
                ProductVariantDto(id = "v1", stock = 3),
                ProductVariantDto(id = "v2", stock = 2),
            ),
        ).toDomain()

        mapped.availableStock shouldBe 5
    }

    @Test
    fun `a product with no stock information reports zero rather than an assumption`() {
        product().toDomain().availableStock shouldBe 0
    }

    @Test
    fun `only a published product with stock is purchasable`() {
        product(inventory = InventoryDto(available = 3)).toDomain().isPurchasable shouldBe true
        product(inventory = InventoryDto(available = 0)).toDomain().isPurchasable shouldBe false
        product(status = "draft", inventory = InventoryDto(available = 3)).toDomain()
            .isPurchasable shouldBe false
        product(status = "pending_review", inventory = InventoryDto(available = 3)).toDomain()
            .isPurchasable shouldBe false
    }

    @Test
    fun `low stock uses the inventory reorder level`() {
        val low = product(inventory = InventoryDto(available = 2, reorderLevel = 5)).toDomain()
        low.isLowStock shouldBe true

        val healthy = product(inventory = InventoryDto(available = 50, reorderLevel = 5)).toDomain()
        healthy.isLowStock shouldBe false
    }

    @Test
    fun `detail images are kept out of the gallery`() {
        val gallery = ProductImageDto(id = "i1", url = "g1", sortOrder = 1)
        val detail = ProductImageDto(id = "i2", url = "d1", sortOrder = 0, imageType = "detail")

        val mapped = product(images = listOf(detail, gallery)).toDomain()

        mapped.images.map { it.url } shouldBe listOf("g1")
        mapped.detailImages.map { it.url } shouldBe listOf("d1")
    }

    @Test
    fun `the primary image wins, otherwise the first gallery image is used`() {
        val gallery = ProductImageDto(id = "i1", url = "g1", sortOrder = 2)
        val primary = ProductImageDto(id = "i2", url = "hero", sortOrder = 1, isPrimary = true)

        product(images = listOf(gallery), primaryImage = primary).toDomain().primaryImageUrl shouldBe "hero"
        product(images = listOf(gallery)).toDomain().primaryImageUrl shouldBe "g1"
        product().toDomain().primaryImageUrl shouldBe null
    }

    @Test
    fun `display urls are preferred, and a missing unit falls back to a real one`() {
        val mapped = product(
            unit = "   ",
            images = listOf(ProductImageDto(id = "i1", url = "raw-key", displayUrl = "https://cdn/x.webp")),
        ).toDomain()

        mapped.unit shouldBe "ชิ้น"
        mapped.images.single().url shouldBe "https://cdn/x.webp"
    }

    @Test
    fun `discount and verification badges come from the mirrored web rules`() {
        val mapped = product(compareAtPrice = 125.0).toDomain()

        mapped.discountPercent shouldBe 20
        mapped.verificationStatus shouldBe VerificationStatus.Verified
        mapped.status shouldBe ProductStatus.Published
    }

    @Test
    fun `a flat category list is nested and ordered like the backend tree`() {
        val flat = listOf(
            CategoryDto(id = "root-b", name = "B", slug = "b", parentId = null, sortOrder = 2),
            CategoryDto(id = "root-a", name = "A", slug = "a", parentId = null, sortOrder = 1),
            CategoryDto(id = "child-a1", name = "A1", slug = "a1", parentId = "root-a", sortOrder = 2),
            CategoryDto(id = "child-a0", name = "A0", slug = "a0", parentId = "root-a", sortOrder = 1),
            CategoryDto(id = "orphan", name = "O", slug = "o", parentId = "missing", sortOrder = 9),
        )

        val tree = flat.toCategoryTree()

        tree.map { it.slug } shouldBe listOf("a", "b", "o")
        tree.first().children.map { it.slug } shouldBe listOf("a0", "a1")
    }

    @Test
    fun `flattening a category reports each node with its depth`() {
        val category = Category(
            id = "root",
            slug = "root",
            name = "Root",
            parentId = null,
            sortOrder = 0,
            imageUrl = null,
            productCount = 3,
            children = listOf(
                Category(
                    id = "child",
                    slug = "child",
                    name = "Child",
                    parentId = "root",
                    sortOrder = 0,
                    imageUrl = null,
                    productCount = 0,
                    children = emptyList(),
                ),
            ),
        )

        category.flatten() shouldBe listOf(category to 0, category.children.single() to 1)
    }

    @Test
    fun `the catalogue query sends only the filters that were set`() {
        val query = CatalogQuery(
            q = "หูฟัง",
            category = null,
            inStock = false,
            verified = true,
            sortBy = "price_asc",
            limit = 20,
            offset = 40,
        )

        query.toQueryMap() shouldBe mapOf(
            "q" to "หูฟัง",
            "verified" to "true",
            "sortBy" to "price_asc",
            "limit" to "20",
            "offset" to "40",
        )
    }

    @Test
    fun `blank filters are omitted, because the backend treats them as a real filter`() {
        CatalogQuery(q = "   ", category = "", shopId = "  ").toQueryMap() shouldBe emptyMap()
    }

    @Test
    fun `an empty query sends no parameters at all`() {
        CatalogQuery().toQueryMap() shouldBe emptyMap()
    }
}
