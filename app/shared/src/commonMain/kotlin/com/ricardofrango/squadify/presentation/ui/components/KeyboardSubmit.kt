package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction

// Forms move through their fields with the keyboard: every field but the last uses ImeAction.Next
// (Compose's default action for Next moves focus to the following field), and the last one uses
// ImeAction.Done with these actions, so its keyboard key does what the form's main button does.

/**
 * Keyboard actions for a form's last field: Done (or Go/Send) closes the keyboard, so the result
 * (loading, an error, the next screen) is visible, and runs [onSubmit], like the form's button.
 */
fun submitKeyboardActions(onSubmit: () -> Unit) = KeyboardActions(
    onDone = { defaultKeyboardAction(ImeAction.Done); onSubmit() },
    onGo = { defaultKeyboardAction(ImeAction.Done); onSubmit() },
    onSend = { defaultKeyboardAction(ImeAction.Done); onSubmit() }
)
