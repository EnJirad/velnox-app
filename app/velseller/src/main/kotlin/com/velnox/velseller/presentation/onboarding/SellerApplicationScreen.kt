package com.velnox.velseller.presentation.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.data.api.UploadPurpose
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxDivider
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxPrimaryButton
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.core.ui.R as SharedR
import com.velnox.velseller.R

/**
 * The seller application form.
 *
 * Reached in two situations and identical in both: a signed-in user who has never
 * applied, and an applicant who was asked for corrections (`needs_correction`) or
 * rejected and is resubmitting. The server decides whether a resubmission is allowed —
 * the backend owns the seller lifecycle — so this screen offers the action and reports
 * any refusal verbatim instead of second-guessing it.
 *
 * Identity documents are uploaded one at a time through the system photo picker, which
 * grants access to the single chosen file and therefore needs no media permission. The
 * app holds a durable R2 object key, never the file.
 */
@Composable
fun SellerApplicationScreen(
    onSubmitted: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SellerApplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Which slot the next picker result belongs to. The picker itself carries no
    // payload back, so the intent has to be remembered across the round trip.
    var pendingPurpose by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val purpose = pendingPurpose
        pendingPurpose = null
        // A null uri means the user dismissed the picker — not an error.
        if (uri != null && purpose != null) viewModel.onDocumentPicked(purpose, uri)
    }

    fun pick(purpose: String) {
        pendingPurpose = purpose
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state.submitted) {
        if (state.submitted) onSubmitted()
    }

    VelnoxScaffold(
        title = stringResource(R.string.velseller_apply_title),
        tabs = emptyList(),
        currentRoute = "",
        onNavigate = {},
        onBack = onBack,
    ) { insets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
            contentPadding = PaddingValues(
                start = VelnoxTokens.spacing.screenHorizontal,
                end = VelnoxTokens.spacing.screenHorizontal,
                top = VelnoxTokens.spacing.gap,
                bottom = VelnoxTokens.spacing.screenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
        ) {
            state.error?.let { error ->
                item {
                    VelnoxMessageBanner(
                        message = error.serverMessage ?: stringResource(SharedR.string.velnox_state_error_body),
                        tone = VelnoxBannerTone.Error,
                        onDismiss = viewModel::consumeError,
                    )
                }
            }

            item {
                VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                    VelnoxSectionHeader(title = stringResource(R.string.velseller_apply_title))

                    VelnoxTextField(
                        value = state.shopName,
                        onValueChange = viewModel::onShopNameChange,
                        label = stringResource(R.string.velseller_apply_shop_name),
                        enabled = !state.submitting,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                    VelnoxTextField(
                        value = state.firstName,
                        onValueChange = viewModel::onFirstNameChange,
                        label = stringResource(R.string.velseller_apply_first_name),
                        enabled = !state.submitting,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                    VelnoxTextField(
                        value = state.lastName,
                        onValueChange = viewModel::onLastNameChange,
                        label = stringResource(R.string.velseller_apply_last_name),
                        enabled = !state.submitting,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                    VelnoxTextField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChange,
                        label = stringResource(R.string.velseller_apply_phone),
                        enabled = !state.submitting,
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                    VelnoxTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = stringResource(R.string.velseller_apply_description),
                        enabled = !state.submitting,
                        singleLine = false,
                        maxLines = 3,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                    VelnoxTextField(
                        value = state.category,
                        onValueChange = viewModel::onCategoryChange,
                        label = stringResource(R.string.velseller_apply_category),
                        enabled = !state.submitting,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                    VelnoxTextField(
                        value = state.idNumber,
                        onValueChange = viewModel::onIdNumberChange,
                        label = stringResource(R.string.velseller_apply_id_number),
                        enabled = !state.submitting,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    )
                }
            }

            item {
                VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                    VelnoxSectionHeader(
                        title = stringResource(R.string.velseller_apply_documents),
                        subtitle = stringResource(R.string.velseller_apply_document_required),
                    )

                    DocumentPickerRow(
                        label = stringResource(R.string.velseller_apply_document_front),
                        state = state.front,
                        enabled = !state.submitting,
                        onPick = { pick(UploadPurpose.ID_CARD) },
                    )
                    VelnoxDivider(modifier = Modifier.padding(vertical = VelnoxTokens.spacing.gapSmall))
                    DocumentPickerRow(
                        label = stringResource(R.string.velseller_apply_document_back),
                        state = state.back,
                        enabled = !state.submitting,
                        onPick = { pick(UploadPurpose.ID_CARD_BACK) },
                    )
                    VelnoxDivider(modifier = Modifier.padding(vertical = VelnoxTokens.spacing.gapSmall))
                    DocumentPickerRow(
                        label = stringResource(R.string.velseller_apply_document_selfie),
                        state = state.selfie,
                        enabled = !state.submitting,
                        onPick = { pick(UploadPurpose.SELFIE_ID) },
                    )

                    Text(
                        text = stringResource(R.string.velseller_apply_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gap),
                    )
                }
            }

            item {
                VelnoxPrimaryButton(
                    text = stringResource(R.string.velseller_apply_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    loading = state.submitting,
                )
            }
        }
    }
}

/** One document slot: what it is, whether it is uploaded, and the picker action. */
@Composable
private fun DocumentPickerRow(
    label: String,
    state: SellerApplicationViewModel.UploadState,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VelnoxTokens.spacing.gapTiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = VelnoxColors.OnSurface,
            )
            when (state) {
                SellerApplicationViewModel.UploadState.Empty -> Unit

                SellerApplicationViewModel.UploadState.Uploading -> Text(
                    text = stringResource(R.string.velseller_apply_document_uploading),
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )

                is SellerApplicationViewModel.UploadState.Uploaded -> Text(
                    text = stringResource(R.string.velseller_apply_document_uploaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.EmeraldOnSurface,
                )

                is SellerApplicationViewModel.UploadState.Failed -> Text(
                    text = when (state.reason) {
                        SellerApplicationViewModel.UploadFailure.UnsupportedType ->
                            stringResource(R.string.velseller_apply_image_unsupported)

                        SellerApplicationViewModel.UploadFailure.TooLarge ->
                            stringResource(R.string.velseller_apply_image_too_large)

                        SellerApplicationViewModel.UploadFailure.Empty ->
                            stringResource(R.string.velseller_apply_image_unsupported)

                        SellerApplicationViewModel.UploadFailure.Upload ->
                            stringResource(SharedR.string.velnox_state_error_body)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.Destructive,
                )
            }
        }

        when (state) {
            SellerApplicationViewModel.UploadState.Uploading -> CircularProgressIndicator(
                color = VelnoxColors.Emerald,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )

            is SellerApplicationViewModel.UploadState.Uploaded -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.velseller_apply_document_uploaded),
                tint = VelnoxColors.EmeraldOnSurface,
                modifier = Modifier.size(24.dp),
            )

            else -> VelnoxSecondaryButton(
                text = stringResource(
                    if (state is SellerApplicationViewModel.UploadState.Failed) {
                        R.string.velseller_apply_document_replace
                    } else {
                        R.string.velseller_apply_document_pick
                    },
                ),
                onClick = onPick,
                enabled = enabled,
            )
        }
    }
}
