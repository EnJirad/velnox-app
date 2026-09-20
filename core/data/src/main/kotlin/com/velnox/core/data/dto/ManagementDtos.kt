package com.velnox.core.data.dto

import com.velnox.core.network.serialization.FlexibleBooleanSerializer
import com.velnox.core.network.serialization.FlexibleDoubleSerializer
import com.velnox.core.network.serialization.FlexibleEpochMillisSerializer
import com.velnox.core.network.serialization.FlexibleIntSerializer
import kotlinx.serialization.Serializable

/**
 * Velseller and VelCenter payloads.
 *
 * Names follow the endpoints that produce them: `backend/routes/seller.ts`
 * (`/api/seller/status`, `/api/seller/profile`, `/api/seller/apply`),
 * `backend/routes/seller-orders.ts`, `backend/routes/seller-intelligence.ts`
 * (`/api/seller/income`, `/api/seller/goals`) and `backend/routes/center.ts`
 * (`/api/admin/*`). `/api/seller/status` answers `data: null` for an authenticated
 * user who has not applied — that is handled by `safeApiCallAllowNull`, not by a
 * fabricated DTO.
 */

/** `GET /api/seller/status`. */
@Serializable
data class SellerStatusDto(
    val status: String? = null,
    val rejectionReason: String? = null,
    val correctionReason: String? = null,
    val rejectionReasonCode: String? = null,
    val correctionReasonCode: String? = null,
    val verificationStatus: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val verifiedAt: Long? = null,
)

/** `GET /api/seller/profile`. Mirrors `SellerProfile` in commerce.ts. */
@Serializable
data class SellerProfileEnvelopeDto(
    val seller: SellerRecordDto? = null,
    val shops: List<ShopDto> = emptyList(),
)

@Serializable
data class SellerRecordDto(
    val id: String,
    val ownerUserId: String? = null,
    val name: String? = null,
    val taxId: String? = null,
    val status: String = "pending",
    val verificationStatus: String? = null,
    val rejectionReason: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val refundPolicyLimit: Double? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
)

/**
 * `POST /api/seller/apply` body.
 *
 * Identity documents are sent as **R2 object keys**, exactly as
 * `RequireRole.tsx` does — the app uploads to R2 first and then references the
 * durable key, so the application record never depends on the device holding the
 * original file.
 */
@Serializable
data class SellerApplicationRequest(
    val shopName: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val shopDescription: String? = null,
    val shopCategory: String? = null,
    val shopAddress: ShopAddressDto? = null,
    val idNumber: String? = null,
    val idCardFrontUrl: String? = null,
    val idCardBackUrl: String? = null,
    val selfieUrl: String? = null,
    val identityEvidence: List<String> = emptyList(),
)

@Serializable
data class ShopAddressDto(
    val line1: String = "",
    val line2: String = "",
    val subdistrict: String = "",
    val district: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "TH",
)

/** `PATCH /api/seller/shop`. */
@Serializable
data class ShopUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val announcement: String? = null,
    val category: String? = null,
)

/** `PATCH /api/seller/products/:productId/status`. */
@Serializable
data class ProductStatusRequest(val status: String, val reason: String? = null)

/** `PATCH /api/seller/products/:productId/stock`. */
@Serializable
data class StockUpdateRequest(val quantity: Int)

// ─── Seller intelligence ─────────────────────────────────────────────────────

/**
 * `GET /api/seller/income`.
 *
 * Every figure is a server aggregate over real orders; the app never computes
 * revenue locally, because a client-side sum would drift from Neon and the whole
 * point of the endpoint is to be the financial record.
 */
@Serializable
data class SellerIncomeDto(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val grossSales: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val commission: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val netEarnings: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val pendingPayout: Double = 0.0,
    @Serializable(with = FlexibleIntSerializer::class)
    val orderCount: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val unitsSold: Int = 0,
    val currency: String = "THB",
    val periodStart: String? = null,
    val periodEnd: String? = null,
)

@Serializable
data class SellerGoalDto(
    val id: String,
    val title: String = "",
    val metric: String = "",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val targetValue: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val currentValue: Double = 0.0,
    val period: String? = null,
    val status: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val dueAt: Long? = null,
)

@Serializable
data class GoalCreateRequest(
    val title: String,
    val metric: String,
    val targetValue: Double,
    val period: String? = null,
    val dueAt: String? = null,
)

// ─── VelCenter ───────────────────────────────────────────────────────────────

/**
 * `GET /api/admin/dashboard/counts`.
 *
 * Counts are optional: the endpoint has grown over time, and a missing tile must
 * render as unavailable rather than as a confident zero.
 */
@Serializable
data class DashboardCountsDto(
    @Serializable(with = FlexibleIntSerializer::class) val pendingSellers: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val pendingProducts: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val totalUsers: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val totalSellers: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val totalOrders: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val totalProducts: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val openVerifications: Int? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val grossMerchandiseValue: Double? = null,
)

/** `GET /api/admin/sellers`. */
@Serializable
data class AdminSellerDto(
    val id: String,
    val ownerUserId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val status: String = "pending",
    val rejectionReason: String? = null,
    val verificationStatus: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
    val shopId: String? = null,
    val shopName: String? = null,
    val shopSlug: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val productCount: Int? = null,
)

/** `PATCH /api/admin/sellers/:sellerId/status`. */
@Serializable
data class SellerStatusUpdateRequest(
    val status: String,
    val reason: String? = null,
    val reasonCode: String? = null,
)

/** `GET /api/admin/products/moderation`. */
@Serializable
data class ModerationProductDto(
    val id: String,
    val name: String = "",
    val status: String = "pending_review",
    val shopId: String? = null,
    val shopName: String? = null,
    val sellerId: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val price: Double = 0.0,
    val currency: String = "THB",
    val categorySlug: String? = null,
    val primaryImageUrl: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val submittedAt: Long = 0L,
    val rejectionReason: String? = null,
)

/** `PATCH /api/admin/products/:productId/moderation`. */
@Serializable
data class ModerationDecisionRequest(
    val status: String,
    val reason: String? = null,
    val reasonCode: String? = null,
)

/** `GET /api/admin/users`. */
@Serializable
data class AdminUserDto(
    val id: String,
    val email: String = "",
    val name: String = "",
    val role: String = "customer",
    val status: String = "active",
    val department: String? = null,
    val avatar: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val mustChangePassword: Boolean = false,
    val employeeId: String? = null,
)

/** `PATCH /api/admin/users/:targetUserId/access`. */
@Serializable
data class UserAccessUpdateRequest(
    val role: String? = null,
    val status: String? = null,
    val reason: String? = null,
)

/** `GET /api/admin/employees`. */
@Serializable
data class EmployeeDto(
    val userId: String,
    val employeeId: String? = null,
    val email: String = "",
    val name: String = "",
    val role: String = "staff",
    val department: String? = null,
    val status: String = "active",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val mustChangePassword: Boolean = false,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class CreateEmployeeRequest(
    val email: String,
    val name: String,
    val role: String = "staff",
    val department: String? = null,
    val permissions: List<String> = emptyList(),
)

/** `GET /api/admin/audit-logs`. */
@Serializable
data class AuditLogDto(
    val id: String,
    val actorId: String? = null,
    val actorEmail: String? = null,
    val action: String = "",
    val entityType: String? = null,
    val entityId: String? = null,
    val metadata: kotlinx.serialization.json.JsonElement? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
    val ipAddress: String? = null,
)

/** `GET /api/admin/settings`. */
@Serializable
data class PlatformSettingDto(
    val key: String,
    val value: kotlinx.serialization.json.JsonElement? = null,
    val description: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlatformSettingUpdateRequest(val key: String, val value: kotlinx.serialization.json.JsonElement)

/** `GET /api/admin/categories`. */
@Serializable
data class AdminCategoryDto(
    val id: String,
    val slug: String = "",
    val name: String = "",
    val icon: String? = null,
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null,
    @kotlinx.serialization.SerialName("sort_order")
    @Serializable(with = FlexibleIntSerializer::class)
    val sortOrder: Int = 0,
    @kotlinx.serialization.SerialName("is_active")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isActive: Boolean = true,
    @kotlinx.serialization.SerialName("image_url") val imageUrl: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val productCount: Int? = null,
)

/**
 * `POST`/`PATCH /api/admin/categories`.
 *
 * `names` carries the per-language labels (th/en/my) the backend already stores in
 * the `categories.names` JSONB column, so VelCenter edits the same localised values
 * the storefront reads.
 */
@Serializable
data class CategoryUpsertRequest(
    val name: String,
    val slug: String,
    val icon: String? = null,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val names: Map<String, String>? = null,
    val descriptions: Map<String, String>? = null,
)

/** `GET /api/admin/permissions` — the catalog VelCenter renders as checkboxes. */
@Serializable
data class PermissionDto(
    val code: String,
    val label: String? = null,
    val group: String? = null,
    val description: String? = null,
)

/** `PATCH /api/admin/employees/:userId/active`. */
@Serializable
data class EmployeeActiveRequest(val active: Boolean)

/** `PATCH /api/admin/staff` — role/department/permission assignment. */
@Serializable
data class StaffProfileRequest(
    val userId: String,
    val role: String? = null,
    val department: String? = null,
    val permissions: List<String>? = null,
)

/** `GET /api/admin/bootstrap-status`. */
@Serializable
data class BootstrapStatusDto(
    @Serializable(with = FlexibleBooleanSerializer::class)
    val ownerExists: Boolean = false,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val configured: Boolean = false,
)

/**
 * `POST /api/admin/claim-owner` body.
 *
 * The value is the operator's `BOOTSTRAP_OWNER_SECRET`, entered by hand on the
 * device. It is never persisted, never logged and never bundled — `VelnoxLog`
 * redacts it by pattern regardless.
 */
@Serializable
data class ClaimOwnerRequest(val bootstrapCode: String)
