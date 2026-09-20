package com.velnox.core.data.api

import com.velnox.core.data.dto.AdminCategoryDto
import com.velnox.core.data.dto.AdminSellerDto
import com.velnox.core.data.dto.AdminUserDto
import com.velnox.core.data.dto.AuditLogDto
import com.velnox.core.data.dto.CategoryUpsertRequest
import com.velnox.core.data.dto.CreateEmployeeRequest
import com.velnox.core.data.dto.DashboardCountsDto
import com.velnox.core.data.dto.EmployeeDto
import com.velnox.core.data.dto.ModerationDecisionRequest
import com.velnox.core.data.dto.ModerationProductDto
import com.velnox.core.data.dto.OrderDto
import com.velnox.core.data.dto.OrderStatusRequest
import com.velnox.core.data.dto.PermissionDto
import com.velnox.core.data.dto.PlatformSettingDto
import com.velnox.core.data.dto.PlatformSettingUpdateRequest
import com.velnox.core.data.dto.SellerStatusUpdateRequest
import com.velnox.core.data.dto.UserAccessUpdateRequest
import com.velnox.core.network.ApiAck
import com.velnox.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * VelCenter endpoints.
 *
 * Every route here is guarded server-side by `requireCenterAccess` plus a specific
 * permission code resolved from `backend/lib/permissions.ts`. The app mirrors those
 * codes only to hide what the user cannot do; the endpoints re-check, so the UI is
 * never the boundary (the same rule `docs/ai/AUTH.md` states for the web client).
 */
interface VelnoxCenterApi {

    @GET("admin/dashboard/counts")
    suspend fun dashboardCounts(): ApiEnvelope<DashboardCountsDto>

    // ─── Sellers ─────────────────────────────────────────────────────────────
    @GET("admin/sellers")
    suspend fun sellers(
        @Query("status") status: String = "all",
        @Query("q") query: String? = null,
    ): ApiEnvelope<List<AdminSellerDto>>

    /**
     * Approving a seller promotes `users.role`, writes an `audit_logs` row and
     * broadcasts on the `seller:updated` channel. The backend locks the row with
     * `FOR UPDATE`, so two admins tapping approve cannot double-approve.
     */
    @PATCH("admin/sellers/{sellerId}/status")
    suspend fun setSellerStatus(
        @Path("sellerId") sellerId: String,
        @Body body: SellerStatusUpdateRequest,
    ): ApiEnvelope<AdminSellerDto>

    // ─── Product moderation ──────────────────────────────────────────────────
    @GET("admin/products/moderation")
    suspend fun moderationQueue(
        @Query("status") status: String? = null,
        @Query("q") query: String? = null,
        @Query("shopId") shopId: String? = null,
    ): ApiEnvelope<List<ModerationProductDto>>

    @PATCH("admin/products/{productId}/moderation")
    suspend fun moderateProduct(
        @Path("productId") productId: String,
        @Body body: ModerationDecisionRequest,
    ): ApiEnvelope<ModerationProductDto>

    // ─── Users & employees ───────────────────────────────────────────────────
    @GET("admin/users")
    suspend fun users(@Query("segment") segment: String = "all"): ApiEnvelope<List<AdminUserDto>>

    @PATCH("admin/users/{targetUserId}/access")
    suspend fun setUserAccess(
        @Path("targetUserId") targetUserId: String,
        @Body body: UserAccessUpdateRequest,
    ): ApiEnvelope<AdminUserDto>

    @GET("admin/employees")
    suspend fun employees(): ApiEnvelope<List<EmployeeDto>>

    /**
     * Creates a staff account. The response carries no password: the backend issues
     * (or e-mails) a temporary one and sets `must_change_password`, which the app
     * enforces by routing the user to the change-password screen on next sign-in.
     */
    @POST("admin/employees")
    suspend fun createEmployee(@Body body: CreateEmployeeRequest): ApiEnvelope<EmployeeDto>

    @POST("admin/employees/{userId}/reset-password")
    suspend fun resetEmployeePassword(@Path("userId") userId: String): ApiEnvelope<ApiAck>

    @PATCH("admin/employees/{userId}/active")
    suspend fun setEmployeeActive(
        @Path("userId") userId: String,
        @Body body: com.velnox.core.data.dto.EmployeeActiveRequest,
    ): ApiEnvelope<ApiAck>

    @GET("admin/permissions")
    suspend fun permissions(): ApiEnvelope<List<PermissionDto>>

    @PATCH("admin/staff")
    suspend fun updateStaffProfile(
        @Body body: com.velnox.core.data.dto.StaffProfileRequest,
    ): ApiEnvelope<EmployeeDto>

    // ─── Orders ──────────────────────────────────────────────────────────────
    @GET("admin/orders")
    suspend fun orders(@Query("limit") limit: Int = 100): ApiEnvelope<List<OrderDto>>

    @PATCH("admin/orders/{orderId}/status")
    suspend fun setOrderStatus(
        @Path("orderId") orderId: String,
        @Body body: OrderStatusRequest,
    ): ApiEnvelope<OrderDto>

    // ─── Audit ───────────────────────────────────────────────────────────────
    @GET("admin/audit-logs")
    suspend fun auditLogs(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("action") action: String? = null,
        @Query("entityType") entityType: String? = null,
        @Query("q") query: String? = null,
    ): ApiEnvelope<List<AuditLogDto>>

    // ─── Categories ──────────────────────────────────────────────────────────
    @GET("admin/categories")
    suspend fun categories(): ApiEnvelope<List<AdminCategoryDto>>

    @POST("admin/categories")
    suspend fun createCategory(@Body body: CategoryUpsertRequest): ApiEnvelope<AdminCategoryDto>

    @PATCH("admin/categories/{categoryId}")
    suspend fun updateCategory(
        @Path("categoryId") categoryId: String,
        @Body body: CategoryUpsertRequest,
    ): ApiEnvelope<AdminCategoryDto>

    /**
     * Deletion is refused by the backend when products or children still reference
     * the category (deactivate instead) — a real integrity rule, not a UI nicety.
     */
    @DELETE("admin/categories/{categoryId}")
    suspend fun deleteCategory(@Path("categoryId") categoryId: String): ApiEnvelope<ApiAck>

    // ─── Settings ────────────────────────────────────────────────────────────
    @GET("admin/settings")
    suspend fun settings(): ApiEnvelope<List<PlatformSettingDto>>

    @PATCH("admin/settings")
    suspend fun updateSetting(@Body body: PlatformSettingUpdateRequest): ApiEnvelope<PlatformSettingDto>

    // ─── Owner bootstrap ─────────────────────────────────────────────────────
    @GET("admin/bootstrap-status")
    suspend fun bootstrapStatus(): ApiEnvelope<com.velnox.core.data.dto.BootstrapStatusDto>

    /**
     * Claims the owner account with the `BOOTSTRAP_OWNER_SECRET`. The secret is
     * supplied by the operator at the moment of claim — it is never stored, logged
     * or bundled.
     */
    @POST("admin/claim-owner")
    suspend fun claimOwner(@Body body: com.velnox.core.data.dto.ClaimOwnerRequest): ApiEnvelope<ApiAck>
}
