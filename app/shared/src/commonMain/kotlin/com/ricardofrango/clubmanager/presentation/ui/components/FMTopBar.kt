package com.ricardofrango.clubmanager.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme
import clubmanager.app.shared.generated.resources.Res
import clubmanager.app.shared.generated.resources.ic_back
import clubmanager.app.shared.generated.resources.label_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FMTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
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
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_back),
                    contentDescription = stringResource(Res.string.label_back),
                    tint = Color.White
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CarbonGray,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

@Preview
@Composable
fun FMTopBarPreview() {
    ClubManagerTheme {
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
    ClubManagerTheme {
        FMTopBar(
            title = "Title",
            onNavigateBack = {}
        )
    }
}