package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.ic_add

/** The app's floating action for a list's main "add": `PitchGreen` with a white [icon] and [text]. */
@Composable
fun PrimaryFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: DrawableResource = Res.drawable.ic_add) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = PitchGreen,
        contentColor = Color.White,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        text = { Text(text, fontWeight = FontWeight.Bold) }
    )
}

@Preview
@Composable
private fun PrimaryFabPreview() {
    SquadifyTheme { PrimaryFab(text = "Add age groups", onClick = {}) }
}
