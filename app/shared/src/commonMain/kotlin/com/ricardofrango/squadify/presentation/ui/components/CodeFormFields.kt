package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen

// The pieces of the two-step "code by email" forms (Forgot password, Change email): an intro line,
// a reserved error line, and a white outlined text field (also used by the opponent form).

@Composable
fun FormIntro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = CarbonGray)
}

/** Reserved space, so an error doesn't push the fields down. */
@Composable
fun FormErrorLine(error: String?) {
    Box(Modifier.fillMaxWidth().heightIn(min = 20.dp), contentAlignment = Alignment.CenterStart) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    // Null for a box too narrow for a floating label (e.g. a rating), which uses a placeholder instead.
    label: String?,
    enabled: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
    placeholder: String? = null,
    // False for a multi-line field (e.g. notes), whose Enter adds a line.
    singleLine: Boolean = true,
    // The height of a multi-line field, in lines (it grows as text is typed).
    minLines: Int = if (singleLine) 1 else 3,
    hidden: Boolean = false,
    onToggleHidden: (() -> Unit)? = null,
    // Next moves to the following field (Compose's default for ImeAction.Next); the last field's
    // action submits, like the button below it.
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = Color.Gray.copy(alpha = 0.7f)) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = onImeAction?.let { submit -> KeyboardActions(onDone = { submit() }, onSend = { submit() }, onGo = { submit() }) }
            ?: KeyboardActions.Default,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = onToggleHidden?.let { toggle -> { PasswordVisibilityToggle(visible = !hidden, onToggle = toggle) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DeepCarbon,
            unfocusedTextColor = DeepCarbon,
            // The error state only changes the border and label: the typed text stays dark.
            errorTextColor = DeepCarbon,
            // Disabled while submitting: dimmed, but still readable.
            disabledTextColor = DeepCarbon.copy(alpha = 0.6f),
            disabledBorderColor = Color.Gray.copy(alpha = 0.2f),
            disabledLabelColor = Color.Gray.copy(alpha = 0.8f),
            disabledSupportingTextColor = Color.Gray.copy(alpha = 0.8f),
            disabledTrailingIconColor = Color.Gray.copy(alpha = 0.6f),
            focusedBorderColor = PitchGreen,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
            focusedLabelColor = PitchGreen,
            unfocusedLabelColor = Color.Gray
        )
    )
}
