package com.ricardofrango.footballmanager.presentation.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.btn_create_season
import footballmanager.app.shared.generated.resources.btn_creating_season
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The app's standard full-width primary action button: fixed 58dp height, `PitchGreen` fill,
 * uppercase extra-bold label, and a spinner swapped in for the label while [isLoading]. Originally
 * duplicated identically across `LoginScreen`, `RegisterScreen`, and `CreateSeasonScreen` -- extracted
 * here so future screens reuse it instead of copying the block again (see AI_GUIDELINES.md on
 * reusable components).
 *
 * @param text Label shown when not loading; rendered uppercase, so pass normal-case copy.
 * @param isLoading Shows a spinner instead of [text] and disables the button while true.
 * @param enabled Additional enablement on top of `!isLoading` (e.g. a form-validity check).
 */
@Composable
fun PrimaryLoadingButton(
    text: StringResource,
    submittingText: StringResource,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PitchGreen,
            contentColor = Color.White,
            disabledContainerColor = PitchGreen.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        if (isLoading) {
            Row {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
                Text(
                    text = stringResource(submittingText).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                )
            }
        } else {
            Text(
                text = stringResource(text).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Preview
@Composable
private fun PrimaryLoadingButtonPreview() {
    PrimaryLoadingButton(
        text = Res.string.btn_create_season,
        submittingText = Res.string.btn_creating_season,
        isLoading = false,
        onClick = {})
}

@Preview
@Composable
private fun PrimaryLoadingButtonSubmittingPreview() {
    PrimaryLoadingButton(
        text = Res.string.btn_create_season,
        submittingText = Res.string.btn_creating_season,
        isLoading = true,
        onClick = {})
}
