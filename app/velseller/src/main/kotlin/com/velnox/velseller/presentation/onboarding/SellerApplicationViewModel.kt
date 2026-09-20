package com.velnox.velseller.presentation.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.api.UploadContentType
import com.velnox.core.data.api.UploadPurpose
import com.velnox.core.data.dto.SellerApplicationRequest
import com.velnox.core.data.repository.SellerRepository
import com.velnox.core.data.repository.UploadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The seller application, including identity documents.
 *
 * ## Why the documents are uploaded before the application is sent
 *
 * `POST /api/seller/apply` takes **R2 object keys**, exactly as the web onboarding
 * does. Bytes are uploaded first, one document at a time, and the keys are then
 * submitted with the form. That ordering is what makes the application record
 * independent of the device: an applicant can close the app, come back, and the
 * evidence is already durable server-side.
 *
 * ## What this class never touches
 *
 * No R2 credential exists here or anywhere in the APK. `core:data`'s
 * `UploadRepository` asks the backend to sign a short-lived URL and PUTs the bytes
 * straight to Cloudflare over a client that carries no Velnox session. Validation
 * (allowed types, 10 MB) mirrors the backend's own rules so a user hears about a
 * wrong file immediately instead of after a wasted upload.
 */
@HiltViewModel
class SellerApplicationViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
    private val uploadRepository: UploadRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Per-document upload state, so one failure cannot blank the whole form. */
    sealed interface UploadState {
        data object Empty : UploadState
        data object Uploading : UploadState
        data class Uploaded(val objectKey: String) : UploadState
        data class Failed(val reason: UploadFailure) : UploadState
    }

    /** Why a document was refused. Rendered from string resources by the screen. */
    enum class UploadFailure {
        UnsupportedType,
        TooLarge,
        Empty,
        Upload,
    }

    data class UiState(
        val shopName: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val phone: String = "",
        val description: String = "",
        val category: String = "",
        val idNumber: String = "",
        val front: UploadState = UploadState.Empty,
        val back: UploadState = UploadState.Empty,
        val selfie: UploadState = UploadState.Empty,
        val submitting: Boolean = false,
        val error: AppError? = null,
        val submitted: Boolean = false,
    ) {
        /** The durable keys the backend stores with the application. */
        val uploadedKeys: List<String>
            get() = listOf(front, back, selfie)
                .mapNotNull { (it as? UploadState.Uploaded)?.objectKey }

        val canSubmit: Boolean
            get() = !submitting &&
                shopName.isNotBlank() &&
                firstName.isNotBlank() &&
                lastName.isNotBlank() &&
                phone.isNotBlank() &&
                uploadedKeys.isNotEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onShopNameChange(value: String) = _state.update { it.copy(shopName = value) }

    fun onFirstNameChange(value: String) = _state.update { it.copy(firstName = value) }

    fun onLastNameChange(value: String) = _state.update { it.copy(lastName = value) }

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onCategoryChange(value: String) = _state.update { it.copy(category = value) }

    fun onIdNumberChange(value: String) = _state.update { it.copy(idNumber = value) }

    fun consumeError() = _state.update { it.copy(error = null) }

    /**
     * Uploads one identity document.
     *
     * @param purpose one of the three identity purposes the backend accepts; anything
     *   else is refused by `UploadRepository` before a request is made.
     */
    fun onDocumentPicked(purpose: String, uri: Uri) {
        updateSlot(purpose, UploadState.Uploading)

        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
                }.getOrNull()
            }

            if (bytes == null || bytes.isEmpty()) {
                updateSlot(purpose, UploadState.Failed(UploadFailure.Empty))
                return@launch
            }

            if (bytes.size.toLong() > UploadContentType.MAX_BYTES) {
                updateSlot(purpose, UploadState.Failed(UploadFailure.TooLarge))
                return@launch
            }

            val contentType = resolveContentType(
                mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull(),
                fileName = uri.lastPathSegment,
            )
            if (contentType == null) {
                updateSlot(purpose, UploadState.Failed(UploadFailure.UnsupportedType))
                return@launch
            }

            when (
                val result = uploadRepository.upload(
                    bytes = bytes,
                    contentType = contentType,
                    purpose = purpose,
                    fileName = uri.lastPathSegment,
                )
            ) {
                is VelnoxResult.Success ->
                    updateSlot(purpose, UploadState.Uploaded(result.data.objectKey))

                is VelnoxResult.Failure -> {
                    updateSlot(purpose, UploadState.Failed(UploadFailure.Upload))
                    // The backend's own message is more specific than anything this
                    // screen could invent, so it is surfaced verbatim.
                    _state.update { it.copy(error = result.error) }
                }
            }
        }
    }

    /** Submits the application with the uploaded keys. */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }

            val request = SellerApplicationRequest(
                shopName = current.shopName.trim(),
                firstName = current.firstName.trim(),
                lastName = current.lastName.trim(),
                phone = current.phone.trim(),
                shopDescription = current.description.trim().takeIf { it.isNotEmpty() },
                shopCategory = current.category.trim().takeIf { it.isNotEmpty() },
                idNumber = current.idNumber.trim().takeIf { it.isNotEmpty() },
                idCardFrontUrl = (current.front as? UploadState.Uploaded)?.objectKey,
                idCardBackUrl = (current.back as? UploadState.Uploaded)?.objectKey,
                selfieUrl = (current.selfie as? UploadState.Uploaded)?.objectKey,
                identityEvidence = current.uploadedKeys,
            )

            when (val result = sellerRepository.apply(request)) {
                is VelnoxResult.Success ->
                    _state.update { it.copy(submitting = false, submitted = true) }

                is VelnoxResult.Failure ->
                    _state.update { it.copy(submitting = false, error = result.error) }
            }
        }
    }

    private fun updateSlot(purpose: String, state: UploadState) {
        _state.update { current ->
            when (purpose) {
                UploadPurpose.ID_CARD -> current.copy(front = state)
                UploadPurpose.ID_CARD_BACK -> current.copy(back = state)
                UploadPurpose.SELFIE_ID -> current.copy(selfie = state)
                // Unsupported purposes never reach the API (UploadPurpose.all is
                // checked by the repository); ignoring them here keeps the UI honest
                // rather than attaching a document to the wrong field.
                else -> current
            }
        }
    }

    /**
     * Resolves the content type the backend will accept.
     *
     * The picker normally reports a correct MIME type, but it can report `image/jpg`
     * (non-standard) or nothing at all for some content providers. Falling back to the
     * file extension keeps a legitimate JPEG from being refused, and anything still
     * unrecognised is refused *before* the upload rather than by Cloudflare.
     */
    private fun resolveContentType(mimeType: String?, fileName: String?): String? {
        val normalised = mimeType?.trim()?.lowercase()
        if (normalised != null && normalised in UploadContentType.all) return normalised
        if (normalised == "image/jpg") return UploadContentType.JPEG

        return when (fileName?.substringAfterLast('.', "")?.lowercase()) {
            "jpg", "jpeg" -> UploadContentType.JPEG
            "png" -> UploadContentType.PNG
            "webp" -> UploadContentType.WEBP
            "avif" -> UploadContentType.AVIF
            else -> null
        }
    }
}
