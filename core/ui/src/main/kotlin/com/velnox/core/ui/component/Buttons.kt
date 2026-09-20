package com.velnox.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.velnox.core.ui.R
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens

/**
 * Primary action — slate-900 on white, matching the web `bg-slate-900 text-white`
 * used for "Buy Now" and every confirm action.
 */
@Composable
fun VelnoxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(VelnoxTokens.spacing.buttonHeight),
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = VelnoxColors.Primary,
            contentColor = VelnoxColors.OnPrimary,
            disabledContainerColor = VelnoxColors.Primary.copy(alpha = 0.4f),
            disabledContentColor = VelnoxColors.OnPrimary,
        ),
    ) {
        ButtonLabel(text = text, loading = loading)
    }
}

/** Add to cart — the brand emerald, per `docs/ai/DESIGN.md`. */
@Composable
fun VelnoxAddToCartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.velnox_action_add_to_cart),
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(VelnoxTokens.spacing.buttonHeight),
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = VelnoxColors.AddToCart,
            contentColor = VelnoxColors.OnPrimary,
            disabledContainerColor = VelnoxColors.Emerald.copy(alpha = 0.4f),
            disabledContentColor = VelnoxColors.OnPrimary,
        ),
    ) {
        ButtonLabel(text = text, loading = loading)
    }
}

@Composable
fun VelnoxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(VelnoxTokens.spacing.buttonHeight),
        enabled = enabled && !loading,
    ) {
        ButtonLabel(text = text, loading = loading)
    }
}

@Composable
private fun ButtonLabel(text: String, loading: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Standard text field.
 *
 * [errorText] is rendered under the field rather than as a toast so a validation
 * failure stays attached to the input that caused it — the same behaviour the web
 * forms have.
 */
@Composable
fun VelnoxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = errorText != null,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        supportingText = (errorText ?: supportingText)?.let { message ->
            { Text(text = message, color = if (errorText != null) VelnoxColors.Destructive else VelnoxColors.OnSurfaceMuted) }
        },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VelnoxColors.Emerald,
            unfocusedBorderColor = VelnoxColors.Border,
            focusedLabelColor = VelnoxColors.Emerald,
            cursorColor = VelnoxColors.Emerald,
        ),
    )
}

@Composable
fun VelnoxPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = errorText != null,
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = VelnoxColors.OnSurfaceMuted,
                )
            }
        },
        supportingText = errorText?.let {
            { Text(text = it, color = VelnoxColors.Destructive) }
        },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VelnoxColors.Emerald,
            unfocusedBorderColor = VelnoxColors.Border,
            cursorColor = VelnoxColors.Emerald,
        ),
    )
}

/** Search box used by the catalogue and every management list. */
@Composable
fun VelnoxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.velnox_action_search),
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        enabled = enabled,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = VelnoxColors.OnSurfaceMuted,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VelnoxColors.Emerald,
            unfocusedBorderColor = VelnoxColors.Border,
            cursorColor = VelnoxColors.Emerald,
        ),
    )
}
