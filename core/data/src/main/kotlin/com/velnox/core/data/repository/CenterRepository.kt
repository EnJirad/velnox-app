package com.velnox.core.data.repository

import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.error.map
import com.velnox.core.data.api.VelnoxCenterApi
import com.velnox.core.data.dto.CategoryUpsertRequest
import com.velnox.core.data.dto.CreateEmployeeRequest
import com.velnox.core.data.dto.EmployeeActiveRequest
import com.velnox.core.data.dto.ModerationDecisionRequest
import com.velnox.core.data.dto.PlatformSettingUpdateRequest
import com.velnox.core.data.dto.SellerStatusUpdateRequest
import com.velnox.core.data.dto.StaffProfileRequest
import com.velnox.core.data.dto.UserAccessUpdateRequest
import com.velnox.core.data.model.AuditEntry
import com.velnox.core.data.model.DashboardCounts
import com.velnox.core.data.model.EmployeeAccount
import com.velnox.core.data.model.ManagedCategory
import com.velnox.core.data.model.ManagedSeller
import com.velnox.core.data.model.ManagedUser
import com.velnox.core.data.model.ModerationItem
import com.velnox.core.data.model.PermissionOption
import com.velnox.core.data.model.PlatformSetting
import com.velnox.core.data.model.toDomain
import com.velnox.core.network.safeApiCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VelCenter operations.
 *
 * ## Authorization is the server's, not this class's
 *
 * `backend/routes/center.ts` re-checks the caller's role **and** the specific
 * permission code on every route, inside a transaction where it matters (seller
 * approval locks the row with `FOR UPDATE` and writes an `audit_logs` entry). The
 * app mirrors those checks only to hide controls it knows will be refused, exactly as
 * `docs/ai/AUTH.md` states for the web client: "UI hiding is not security".
 *
 * ## Destructive actions keep their reasons
 *
 * Rejecting a seller, rejecting a product and suspending an account all require a
 * machine-readable `reasonCode` plus free text, because the backend stores them and
 * the affected party is shown them (`reviewReason.*` keys on web). Dropping the
 * reason to make the UI simpler would remove information the seller is entitled to.
 */
@Singleton
class CenterRepository @Inject constructor(
    private val api: VelnoxCenterApi,
    private val json: Json,
) {

    suspend fun dashboardCounts(): VelnoxResult<DashboardCounts> =
        safeApiCall(json) { api.dashboardCounts() }.map { it.toDomain() }

    suspend fun sellers(status: String = "all", query: String? = null): VelnoxResult<List<ManagedSeller>> =
        safeApiCall(json) { api.sellers(status, query?.takeIf { it.isNotBlank() }) }
            .map { list -> list.map { it.toDomain() } }

    /**
     * Approves, rejects or suspends a seller.
     *
     * Approval promotes `users.role` to `seller`; the backend blocks self-approval and
     * writes the audit entry, so this is the only supported path.
     */
    suspend fun setSellerStatus(
        sellerId: String,
        status: String,
        reason: String? = null,
        reasonCode: String? = null,
    ): VelnoxResult<ManagedSeller> {
        if (status in REASONS_REQUIRED_FOR_SELLER && reason.isNullOrBlank() && reasonCode.isNullOrBlank()) {
            return VelnoxResult.Failure(
                AppError.Validation(serverMessage = "A reason is required for this decision."),
            )
        }
        return safeApiCall(json) {
            api.setSellerStatus(sellerId, SellerStatusUpdateRequest(status, reason, reasonCode))
        }.map { it.toDomain() }
    }

    suspend fun moderationQueue(
        status: String? = null,
        query: String? = null,
    ): VelnoxResult<List<ModerationItem>> =
        safeApiCall(json) {
            api.moderationQueue(status?.takeIf { it.isNotBlank() }, query?.takeIf { it.isNotBlank() })
        }.map { list -> list.map { it.toDomain() } }

    suspend fun moderateProduct(
        productId: String,
        status: String,
        reason: String? = null,
        reasonCode: String? = null,
    ): VelnoxResult<ModerationItem> {
        if (status == MODERATION_REJECTED && reason.isNullOrBlank()) {
            return VelnoxResult.Failure(
                AppError.Validation(serverMessage = "A rejection reason is required."),
            )
        }
        return safeApiCall(json) {
            api.moderateProduct(productId, ModerationDecisionRequest(status, reason, reasonCode))
        }.map { it.toDomain() }
    }

    suspend fun users(segment: String = "all"): VelnoxResult<List<ManagedUser>> =
        safeApiCall(json) { api.users(segment) }.map { list -> list.map { it.toDomain() } }

    /**
     * Changes a user's role or status.
     *
     * Sent as a partial update: only the fields the operator actually changed are
     * included, so an access edit cannot accidentally reset a role.
     */
    suspend fun setUserAccess(
        userId: String,
        role: String? = null,
        status: String? = null,
        reason: String? = null,
    ): VelnoxResult<ManagedUser> {
        if (role == null && status == null) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "Nothing to update."))
        }
        return safeApiCall(json) {
            api.setUserAccess(userId, UserAccessUpdateRequest(role = role, status = status, reason = reason))
        }.map { it.toDomain() }
    }

    suspend fun employees(): VelnoxResult<List<EmployeeAccount>> =
        safeApiCall(json) { api.employees() }.map { list -> list.map { it.toDomain() } }

    suspend fun createEmployee(
        email: String,
        name: String,
        role: String,
        department: String?,
        permissions: List<String>,
    ): VelnoxResult<EmployeeAccount> {
        if (email.isBlank() || !email.contains('@')) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "A valid e-mail is required."))
        }
        if (name.isBlank()) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "A name is required."))
        }
        return safeApiCall(json) {
            api.createEmployee(
                CreateEmployeeRequest(
                    email = email.trim().lowercase(),
                    name = name.trim(),
                    role = role,
                    department = department,
                    permissions = permissions,
                ),
            )
        }.map { it.toDomain() }
    }

    suspend fun resetEmployeePassword(userId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.resetEmployeePassword(userId) }.map { }

    suspend fun setEmployeeActive(userId: String, active: Boolean): VelnoxResult<Unit> =
        safeApiCall(json) { api.setEmployeeActive(userId, EmployeeActiveRequest(active)) }.map { }

    suspend fun permissions(): VelnoxResult<List<PermissionOption>> =
        safeApiCall(json) { api.permissions() }.map { list -> list.map { it.toDomain() } }

    suspend fun updateStaffProfile(
        userId: String,
        role: String?,
        department: String?,
        permissions: List<String>?,
    ): VelnoxResult<EmployeeAccount> =
        safeApiCall(json) {
            api.updateStaffProfile(StaffProfileRequest(userId, role, department, permissions))
        }.map { it.toDomain() }

    suspend fun auditLogs(
        limit: Int = 100,
        offset: Int = 0,
        action: String? = null,
        query: String? = null,
    ): VelnoxResult<List<AuditEntry>> =
        safeApiCall(json) {
            api.auditLogs(limit = limit, offset = offset, action = action, query = query?.takeIf { it.isNotBlank() })
        }.map { list -> list.map { it.toDomain() } }

    suspend fun categories(): VelnoxResult<List<ManagedCategory>> =
        safeApiCall(json) { api.categories() }.map { list -> list.map { it.toDomain() } }

    suspend fun createCategory(request: CategoryUpsertRequest): VelnoxResult<ManagedCategory> {
        validateCategory(request)?.let { return VelnoxResult.Failure(it) }
        return safeApiCall(json) { api.createCategory(request) }.map { it.toDomain() }
    }

    suspend fun updateCategory(categoryId: String, request: CategoryUpsertRequest): VelnoxResult<ManagedCategory> {
        validateCategory(request)?.let { return VelnoxResult.Failure(it) }
        return safeApiCall(json) { api.updateCategory(categoryId, request) }.map { it.toDomain() }
    }

    suspend fun deleteCategory(categoryId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.deleteCategory(categoryId) }.map { }

    suspend fun settings(): VelnoxResult<List<PlatformSetting>> =
        safeApiCall(json) { api.settings() }.map { list -> list.map { it.toDomain() } }

    suspend fun updateSetting(key: String, value: JsonElement): VelnoxResult<PlatformSetting> =
        safeApiCall(json) { api.updateSetting(PlatformSettingUpdateRequest(key, value)) }.map { it.toDomain() }

    suspend fun bootstrapStatus(): VelnoxResult<Boolean> =
        safeApiCall(json) { api.bootstrapStatus() }.map { it.ownerExists }

    /** Claims the owner account with the operator's bootstrap secret. Never stored. */
    suspend fun claimOwner(bootstrapCode: String): VelnoxResult<Unit> {
        if (bootstrapCode.isBlank()) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "The bootstrap code is required."))
        }
        return safeApiCall(json) {
            api.claimOwner(com.velnox.core.data.dto.ClaimOwnerRequest(bootstrapCode.trim()))
        }.map { }
    }

    /**
     * Client-side mirror of `slugify` in `backend/routes/products.ts` plus the rules
     * `docs/ai/CATEGORIES.md` states: lowercase, hyphenated, URL-safe and
     * language-invariant.
     */
    private fun validateCategory(request: CategoryUpsertRequest): AppError? = when {
        request.name.isBlank() -> AppError.Validation(serverMessage = "A category name is required.")
        request.slug.isBlank() -> AppError.Validation(serverMessage = "A slug is required.")
        !SLUG_PATTERN.matches(request.slug) ->
            AppError.Validation(
                serverMessage = "The slug must be lowercase letters, digits and hyphens only.",
            )
        else -> null
    }

    private companion object {
        val SLUG_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

        /** Decisions the backend refuses to record without a reason. */
        val REASONS_REQUIRED_FOR_SELLER = setOf("rejected", "suspended", "needs_correction")

        const val MODERATION_REJECTED = "rejected"
    }
}
