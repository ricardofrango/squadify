package com.ricardofrango.clubmanager.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.Res
import clubmanager.app.shared.generated.resources.ic_calendar
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The "nothing here yet" state used wherever a club is missing something it needs before the rest of
 * a screen makes sense (first a club with no seasons, now one with seasons but no age groups -- see
 * `ClubDetailsScreen`'s `NoSeasonsState`/`NoAgeGroupsState`). Same icon-title-subtitle-button layout
 * every time; only the copy, icon, and [canAct] permission gate differ per call site.
 *
 * @param canAct Whether the current user can fix this themselves (COORDINATOR+, see
 *   `CheckUserPermissionUseCase`). Drives both the title/subtitle styling (an error-red framing when
 *   true vs. a plain gray one when false) and whether [actionLabel]'s button shows at all -- a plain
 *   member has no way to act, so no button is offered.
 */
@Composable
fun EmptyStateCallToAction(
    icon: DrawableResource,
    title: String,
    subtitle: String,
    canAct: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = DeepCarbon.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        EmptyStateMessage(
            text = title,
            color = if (canAct) MaterialTheme.colorScheme.error else DeepCarbon,
            bold = true
        )
        Spacer(modifier = Modifier.height(4.dp))
        EmptyStateMessage(
            text = subtitle,
            color = if (canAct) MaterialTheme.colorScheme.error else DeepCarbon.copy(alpha = 0.6f)
        )
        if (canAct) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(text: String, color: Color, bold: Boolean = false) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        ),
        textAlign = TextAlign.Center
    )
}

@Preview
@Composable
private fun EmptyStateCallToActionAdminPreview() {
    ClubManagerTheme {
        EmptyStateCallToAction(
            icon = Res.drawable.ic_calendar,
            title = "This club has no seasons yet.",
            subtitle = "Teams and players can't be organised until one exists.",
            canAct = true,
            actionLabel = "Add Season",
            onActionClick = {}
        )
    }
}

@Preview
@Composable
private fun EmptyStateCallToActionMemberPreview() {
    ClubManagerTheme {
        EmptyStateCallToAction(
            icon = Res.drawable.ic_calendar,
            title = "This club has no seasons yet.",
            subtitle = "Ask a coordinator to set one up.",
            canAct = false,
            actionLabel = "Add Season",
            onActionClick = {}
        )
    }
}
