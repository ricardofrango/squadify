package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.ic_cloud_off
import squadify.app.shared.generated.resources.msg_check_connection

private val MessageText = DeepCarbon.copy(alpha = 0.62f)

/**
 * A whole screen's message, the match report's look: an icon in a grey circle, a bold title, a grey
 * body and one button. [primary] fills the button with the club's colour (Retry); otherwise it's outlined.
 */
@Composable
fun FullScreenMessage(
    icon: DrawableResource,
    title: String,
    body: String?,
    buttonLabel: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Column(
        modifier.background(ScreenBackground).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(84.dp).background(CarbonGray.copy(alpha = 0.06f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MessageText, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MessageText, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        if (primary) {
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Text(buttonLabel, fontWeight = FontWeight.Black)
            }
        } else {
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                Text(buttonLabel, color = CarbonGray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A screen that couldn't load: every one looks like the match report's, with the cloud icon and a
 * filled Retry. [body] defaults to "Check your connection and try again".
 */
@Composable
fun ErrorScreen(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    body: String? = stringResource(Res.string.msg_check_connection)
) {
    FullScreenMessage(
        icon = Res.drawable.ic_cloud_off,
        title = title,
        body = body,
        buttonLabel = stringResource(Res.string.btn_retry),
        primary = true,
        onClick = onRetry,
        modifier = modifier
    )
}

@Preview
@Composable
private fun ErrorScreenPreview() {
    SquadifyTheme { ErrorScreen(title = "Couldn't load the team", onRetry = {}) }
}
