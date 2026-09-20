package com.velnox.core.ui.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.ui.R
import com.velnox.core.ui.component.VelnoxPasswordField
import com.velnox.core.ui.component.VelnoxPrimaryButton
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens

/**
 * The Velnox sign-in screen, shared by Velshop, Velseller and VelCenter.
 *
 * ## Design intent
 *
 * The web apps send the user to a Google-hosted page, so there is nothing to copy
 * visually. This screen keeps the parts that carry the Velnox identity — the
 * `#f8fafc` page, a single white card, the emerald accent, the slate-900 primary
 * action — and adapts the layout to a phone rather than shrinking a desktop page.
 *
 * ## Staff sign-in
 *
 * VelCenter's accounts are created by the owner with a password, not with Google
 * (`POST /api/auth/member-login` accepts an e-mail **or** an employee id). That form
 * is shown only when [allowStaffSignIn] is set, so the customers and sellers who use
 * the other two apps never see a password field they could not use.
 */
@Composable
fun VelnoxAuthScreen(
    title: String,
    modifier: Modifier = Modifier,
    allowStaffSignIn: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var staffIdentifier by remember { mutableStateOf("") }
    var staffPassword by remember { mutableStateOf("") }
    var showStaffForm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        notice = when (current) {
            AuthViewModel.Message.GoogleSignInCancelled ->
                context.getString(R.string.velnox_auth_cancelled)
            AuthViewModel.Message.GoogleNotConfigured ->
                context.getString(R.string.velnox_auth_not_configured)
            AuthViewModel.Message.NoGoogleAccount ->
                context.getString(R.string.velnox_auth_no_account)
            is AuthViewModel.Message.Failure ->
                current.error.serverMessage ?: context.getString(R.string.velnox_state_error_body)
        }
        viewModel.consumeMessage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VelnoxColors.Background)
            .padding(VelnoxTokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = VelnoxColors.Surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(VelnoxTokens.spacing.gapSection),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = VelnoxColors.EmeraldSurface, shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = VelnoxColors.EmeraldOnSurface,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp),
                    )
                }

                Spacer(Modifier.height(VelnoxTokens.spacing.gapLarge))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = VelnoxColors.OnSurface,
                )
                Text(
                    text = stringResource(R.string.velnox_auth_sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
                )

                Spacer(Modifier.height(VelnoxTokens.spacing.gapSection))

                VelnoxPrimaryButton(
                    text = stringResource(R.string.velnox_auth_sign_in_google),
                    onClick = { (context as? Activity)?.let(viewModel::signInWithGoogle) },
                    enabled = viewModel.isGoogleSignInConfigured,
                    loading = busy && !showStaffForm,
                )

                if (!viewModel.isGoogleSignInConfigured) {
                    Text(
                        text = stringResource(R.string.velnox_auth_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.WarningOnSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gap),
                    )
                }

                if (allowStaffSignIn) {
                    Spacer(Modifier.height(VelnoxTokens.spacing.gapLarge))

                    if (!showStaffForm) {
                        VelnoxSecondaryButton(
                            text = STAFF_SIGN_IN_LABEL,
                            onClick = { showStaffForm = true },
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap)) {
                            VelnoxTextField(
                                value = staffIdentifier,
                                onValueChange = { staffIdentifier = it },
                                label = STAFF_IDENTIFIER_LABEL,
                                enabled = !busy,
                                imeAction = ImeAction.Next,
                            )
                            VelnoxPasswordField(
                                value = staffPassword,
                                onValueChange = { staffPassword = it },
                                label = STAFF_PASSWORD_LABEL,
                                enabled = !busy,
                            )
                            VelnoxPrimaryButton(
                                text = stringResource(R.string.velnox_action_confirm),
                                onClick = { viewModel.signInStaff(staffIdentifier, staffPassword) },
                                enabled = staffIdentifier.isNotBlank() && staffPassword.isNotEmpty(),
                                loading = busy,
                            )
                        }
                    }
                }

                if (busy) {
                    CircularProgressIndicator(
                        color = VelnoxColors.Emerald,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(top = VelnoxTokens.spacing.gapLarge)
                            .size(20.dp),
                    )
                }

                notice?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.Destructive,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapLarge),
                    )
                }
            }
        }
    }
}

private const val STAFF_SIGN_IN_LABEL = "เข้าสู่ระบบพนักงาน (VelCenter)"
private const val STAFF_IDENTIFIER_LABEL = "อีเมล หรือ รหัสพนักงาน"
private const val STAFF_PASSWORD_LABEL = "รหัสผ่าน"
