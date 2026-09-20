package com.velnox.core.data.repository

import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.error.map
import com.velnox.core.data.api.VelnoxCommerceApi
import com.velnox.core.data.dto.AddressUpsertRequest
import com.velnox.core.data.dto.CustomerProfileDto
import com.velnox.core.data.dto.WishlistToggleRequest
import com.velnox.core.data.model.Address
import com.velnox.core.data.model.CustomerProfile
import com.velnox.core.data.model.NotificationItem
import com.velnox.core.data.model.WishlistEntry
import com.velnox.core.data.model.toDomain
import com.velnox.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Customer-side account data: profile, addresses, wishlist, notifications.
 *
 * Everything here requires authentication server-side (`requireAuth`), so the app
 * never sends a user id — the session decides whose data is returned. That is also
 * why ownership checks cannot be bypassed from the client.
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val api: VelnoxCommerceApi,
    private val json: Json,
) {

    private val _unreadNotifications = MutableStateFlow(0)

    /** Unread notification count, refreshed by [notifications]. */
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    suspend fun profile(): VelnoxResult<CustomerProfile> =
        safeApiCall(json) { api.profile() }.map { it.toDomain() }

    suspend fun updateProfile(profile: CustomerProfile): VelnoxResult<CustomerProfile> {
        if (profile.phone?.isNotBlank() == true && !isPlausiblePhone(profile.phone)) {
            return VelnoxResult.Failure(
                AppError.Validation(serverMessage = "Please enter a valid phone number."),
            )
        }
        return safeApiCall(json) {
            api.updateProfile(
                CustomerProfileDto(
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    phone = profile.phone,
                    preferredLanguage = profile.preferredLanguage,
                ),
            )
        }.map { it.toDomain() }
    }

    suspend fun addresses(): VelnoxResult<List<Address>> =
        safeApiCall(json) { api.addresses() }.map { list -> list.map { it.toDomain() } }

    /**
     * Creates or updates an address.
     *
     * The Thai address shape (line, subdistrict, district, city, postal code) is
     * validated before sending so the user gets an inline field error instead of a
     * generic server rejection; the backend still re-validates.
     */
    suspend fun saveAddress(addressId: String?, body: AddressUpsertRequest): VelnoxResult<Address> {
        validateAddress(body)?.let { return VelnoxResult.Failure(it) }

        val result = if (addressId == null) {
            safeApiCall(json) { api.createAddress(body) }
        } else {
            safeApiCall(json) { api.updateAddress(addressId, body) }
        }
        return result.map { it.toDomain() }
    }

    suspend fun deleteAddress(addressId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.deleteAddress(addressId) }.map { }

    suspend fun wishlist(): VelnoxResult<List<WishlistEntry>> =
        safeApiCall(json) { api.wishlist() }.map { list ->
            list.map { dto ->
                WishlistEntry(
                    productId = dto.productId,
                    product = dto.product?.toDomain(),
                    addedAtEpochMillis = dto.createdAt.takeIf { it > 0L },
                )
            }
        }

    suspend fun toggleWishlist(productId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.toggleWishlist(WishlistToggleRequest(productId)) }.map { }

    suspend fun notifications(): VelnoxResult<List<NotificationItem>> {
        val result = safeApiCall(json) { api.notifications() }.map { list -> list.map { it.toDomain() } }
        if (result is VelnoxResult.Success) {
            _unreadNotifications.value = result.data.count { !it.isRead }
        }
        return result
    }

    suspend fun markNotificationRead(notificationId: String): VelnoxResult<Unit> =
        safeApiCall(json) { api.markNotificationRead(notificationId) }.map { }

    suspend fun markAllNotificationsRead(): VelnoxResult<Unit> =
        safeApiCall(json) { api.markAllNotificationsRead() }.map { }

    private fun validateAddress(body: AddressUpsertRequest): AppError? = when {
        body.recipientName.isBlank() ->
            AppError.Validation(serverMessage = "Recipient name is required.")
        body.phone.isBlank() ->
            AppError.Validation(serverMessage = "Phone number is required.")
        !isPlausiblePhone(body.phone) ->
            AppError.Validation(serverMessage = "Please enter a valid phone number.")
        body.line1.isBlank() ->
            AppError.Validation(serverMessage = "Address line is required.")
        else -> null
    }

    /** Thai mobile or landline, matching the shape the web forms accept. */
    private fun isPlausiblePhone(phone: String?): Boolean {
        val digits = phone?.filter { it.isDigit() } ?: return false
        return digits.length in 9..10
    }
}

/** Converts a domain address back into the request body the API expects. */
fun Address.toUpsertRequest(): AddressUpsertRequest = AddressUpsertRequest(
    recipientName = recipientName,
    phone = phone,
    line1 = line1,
    line2 = line2,
    subdistrict = subdistrict,
    district = district,
    city = city,
    state = state,
    postalCode = postalCode,
    country = country,
    isDefault = isDefault,
)

