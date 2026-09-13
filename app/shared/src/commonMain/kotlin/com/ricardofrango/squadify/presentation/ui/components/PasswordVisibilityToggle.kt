package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.label_hide_password
import squadify.app.shared.generated.resources.label_show_password

/**
 * Toggles a password field between masked and plain text. Drawn as a plain eye (an outlined lens
 * + pupil, struck through when hidden) rather than an imported icon asset, same reasoning as
 * [SquadifyMark] -- crisp at any size, no bitmap to ship.
 */
@Composable
fun PasswordVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Gray
) {
    val description = stringResource(if (visible) Res.string.label_hide_password else Res.string.label_show_password)
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = w * 0.09f
            drawOval(
                color = tint,
                topLeft = Offset(w * 0.06f, h * 0.3f),
                size = Size(w * 0.88f, h * 0.4f),
                style = Stroke(width = strokeWidth)
            )
            drawCircle(color = tint, radius = h * 0.12f, center = Offset(w / 2f, h / 2f))
            if (!visible) {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.12f, h * 0.14f),
                    end = Offset(w * 0.88f, h * 0.86f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
