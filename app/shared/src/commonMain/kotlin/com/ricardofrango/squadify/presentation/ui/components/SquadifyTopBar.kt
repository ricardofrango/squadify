package com.ricardofrango.squadify.presentation.ui.components

import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.ic_back
import squadify.app.shared.generated.resources.label_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * When [showBackButton] is false (e.g. a single-club user's root ClubDetailsScreen, which has no
 * clubs list left to go back to), the title switches to a centered layout with no navigation icon
 * instead of the usual leading-aligned title + back arrow -- Material3 doesn't support centering just
 * the title of a plain [TopAppBar], so this picks [CenterAlignedTopAppBar] instead in that case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadifyTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = true,
    onNavigateBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    // Without a back button: an icon in its place, e.g. the coach home's ☰.
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val titleContent = @Composable {
        Column(horizontalAlignment = if (showBackButton) Alignment.Start else Alignment.CenterHorizontally) {
            // One line each, cut with "…": a long club or team name never makes the bar taller.
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(
                            alpha = 0.7f
                        )
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    val navigationIcon = @Composable {
        if (showBackButton) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_back),
                    contentDescription = stringResource(Res.string.label_back),
                    tint = Color.White
                )
            }
        } else if (leadingIcon != null) {
            leadingIcon()
        }
    }

    if (showBackButton) {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = TopBarInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ClubChrome,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )
    } else {
        CenterAlignedTopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = TopBarInsets,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = ClubChrome,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
    }
}

// The bar's colour runs to the screen's edges; its title and icons keep clear of the status bar and,
// on a phone lying down, of the camera cutout at the side (Material's default leaves the cutout out).
private val TopBarInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

@Preview
@Composable
fun SquadifyTopBarPreview() {
    SquadifyTheme {
        SquadifyTopBar(
            title = "Title",
            subtitle = "Subtitle",
            onNavigateBack = {}
        )
    }
}

@Preview
@Composable
fun SquadifyTopBarNoSubtitlePreview() {
    SquadifyTheme {
        SquadifyTopBar(
            title = "Title",
            onNavigateBack = {}
        )
    }
}

@Preview
@Composable
fun SquadifyTopBarLongTitlePreview() {
    SquadifyTheme {
        SquadifyTopBar(
            title = "Grupo Desportivo e Recreativo de Fontelas e Arredores",
            subtitle = "Sub-15 A · Época 2026/27 · Campeonato Distrital da Primeira Divisão",
            onNavigateBack = {}
        )
    }
}

@Preview
@Composable
fun SquadifyTopBarNoBackButtonPreview() {
    SquadifyTheme {
        SquadifyTopBar(
            title = "Title",
            subtitle = "Subtitle",
            showBackButton = false
        )
    }
}