package com.velnox.core.data.repository

import com.velnox.core.common.domain.SellerStatus
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.error.map
import com.velnox.core.data.api.VelnoxSellerApi
import com.velnox.core.data.dto.GoalCreateRequest
import com.velnox.core.data.dto.ProductStatusRequest
import com.velnox.core.data.dto.SellerApplicationRequest
import com.velnox.core.data.dto.ShopUpdateRequest
import com.velnox.core.data.dto.StockUpdateRequest
import com.velnox.core.data.model.Product
import com.velnox.core.data.model.SellerGoal
import com.velnox.core.data.model.SellerIncome
import com.velnox.core.data.model.SellerProfile
import com.velnox.core.data.model.SellerStatusInfo
import com.velnox.core.data.model.toDomain
import com.velnox.core.network.safeApiCall
import com.velnox.core.network.safeApiCallAllowNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Velseller: application state, shop, own products, income and goals.
 *
 * ## Why `sellerStatus` uses the nullable API call
 *
 * `GET /api/seller/status` answers `{ success: true, data: null }` for a signed-in
 * user who has never applied. `docs/ai/SELLER.md` calls out the exact bug this
 * causes on web: "`seller === null` conflated with loading". The repository therefore
 * models the three states explicitly — [SellerStatusInfo] present, `null` meaning
 * *no application*, and a failure meaning *unknown* — so the UI can never show a
 * registration form to someone whose status merely failed to load.
 */
@Singleton
class SellerRepository @Inject constructor(
    private val api: VelnoxSellerApi,
    private val json: Json,
) {

    /**
     * `data: null` → `Success(null)`, meaning "no application yet".
     * A `Failure` means the status is unknown and must be retried, not assumed.
     */
    suspend fun status(): VelnoxResult<SellerStatusInfo?> =
        safeApiCallAllowNull(json) { api.status() }.map { it?.toDomain() }

    suspend fun profile(): VelnoxResult<SellerProfile> =
        safeApiCall(json) { api.profile() }.map { it.toDomain() }

    /**
     * Submits a seller application.
     *
     * The client mirrors the backend's own preconditions, taken from
     * `RequireRole.tsx` on web: a shop name, an applicant identity, and persisted
     * identity evidence. The evidence must already exist in R2 — the request carries
     * durable object keys, never bytes or `File` references, so the application record
     * does not depend on the device.
     */
    suspend fun apply(request: SellerApplicationRequest): VelnoxResult<SellerStatusInfo> {
        validateApplication(request)?.let { return VelnoxResult.Failure(it) }

        return safeApiCall(json) { api.apply(request) }.map { it.toDomain() }
    }

    suspend fun updateShop(request: ShopUpdateRequest): VelnoxResult<Unit> =
        safeApiCall(json) { api.updateShop(request) }.map { }

    suspend fun products(): VelnoxResult<List<Product>> =
        safeApiCall(json) { api.products() }.map { list -> list.map { it.toDomain() } }

    /**
     * Moves a product through the lifecycle.
     *
     * `draft → pending_review` (submit) and `rejected → pending_review` (resubmit)
     * are the transitions the seller app offers; publishing is a VelCenter decision,
     * mirroring `backend/lib/product-lifecycle.ts`.
     */
    suspend fun setProductStatus(productId: String, status: String, reason: String? = null): VelnoxResult<Product> =
        safeApiCall(json) { api.setProductStatus(productId, ProductStatusRequest(status, reason)) }
            .map { it.toDomain() }

    suspend fun setStock(productId: String, quantity: Int): VelnoxResult<Unit> {
        if (quantity < 0) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "Stock cannot be negative."))
        }
        return safeApiCall(json) { api.setStock(productId, StockUpdateRequest(quantity)) }.map { }
    }

    suspend fun deleteProduct(productId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.deleteProduct(productId) }.map { }

    suspend fun income(): VelnoxResult<SellerIncome> =
        safeApiCall(json) { api.income() }.map { it.toDomain() }

    suspend fun goals(): VelnoxResult<List<SellerGoal>> =
        safeApiCall(json) { api.goals() }.map { list -> list.map { it.toDomain() } }

    suspend fun createGoal(title: String, metric: String, target: Double, period: String?): VelnoxResult<SellerGoal> {
        if (title.isBlank()) return VelnoxResult.Failure(AppError.Validation(serverMessage = "A goal title is required."))
        if (target <= 0.0) return VelnoxResult.Failure(AppError.Validation(serverMessage = "The target must be greater than zero."))

        return safeApiCall(json) {
            api.createGoal(GoalCreateRequest(title = title, metric = metric, targetValue = target, period = period))
        }.map { it.toDomain() }
    }

    suspend fun deleteGoal(goalId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.deleteGoal(goalId) }.map { }

    private fun validateApplication(request: SellerApplicationRequest): AppError? = when {
        request.shopName.isBlank() ->
            AppError.Validation(serverMessage = "A shop name is required.")
        request.firstName.isBlank() || request.lastName.isBlank() ->
            AppError.Validation(serverMessage = "Applicant first and last name are required.")
        request.phone.isBlank() ->
            AppError.Validation(serverMessage = "A contact phone number is required.")
        request.identityEvidence.isEmpty() ->
            AppError.Validation(
                serverMessage = "Identity documents must be uploaded before applying.",
            )
        else -> null
    }
}

/** The statuses a seller may move an order to, from the backend state machine. */
val SellerStatus.canResubmitApplication: Boolean
    get() = this == SellerStatus.Rejected ||
        this == SellerStatus.NeedsCorrection ||
        this == SellerStatus.Unknown
