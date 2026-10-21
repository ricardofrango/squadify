package com.ricardofrango.squadify.presentation.ui.components

import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
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
fun FMTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = true,
    onNavigateBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleContent = @Composable {
        Column(horizontalAlignment = if (showBackButton) Alignment.Start else Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(
                            alpha = 0.7f
                        )
                    )
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
        }
    }

    if (showBackButton) {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actions,
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
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = ClubChrome,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
    }
}

@Preview
@Composable
fun FMTopBarPreview() {
    SquadifyTheme {
        FMTopBar(
            title = "Title",
            subtitle = "Subtitle",
            onNavigateBack = {}
        )
    }
}

@Preview
@Composable
fun FMTopBarNoSubtitlePreview() {
    SquadifyTheme {
        FMTopBar(
            title = "Title",
            onNavigateBack = {}
        )
    }
}

@Preview
@Composable
fun FMTopBarNoBackButtonPreview() {
    SquadifyTheme {
        FMTopBar(
            title = "Title",
            subtitle = "Subtitle",
            showBackButton = false
        )
    }
}