package com.ricardofrango.squadify.presentation.ui.components

import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// The tab bar of the club screen and the detail screens (designs 32, 33): a dark bottom bar on a
// compact window, a left rail from a medium-width window up (useNavigationRail).

/** [dot] marks the tab with a small amber dot, e.g. unsaved changes on it (design 34). */
data class DetailTab(val icon: DrawableResource, val label: StringResource, val dot: Boolean = false)

@Composable
private fun TabIcon(tab: DetailTab) {
    Box {
        Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp))
        if (tab.dot) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Amber, CircleShape))
    }
}

@Composable
fun DetailBottomBar(tabs: List<DetailTab>, selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = ClubChrome, tonalElevation = 0.dp, windowInsets = NavigationBarDefaults.windowInsets) {
        tabs.forEachIndexed { i, tab ->
            val isSelected = i == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(i) },
                icon = { TabIcon(tab) },
                label = {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ChromeAccent,
                    selectedTextColor = ChromeAccent,
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f),
                    indicatorColor = ChromeAccent.copy(alpha = 0.15f)
                )
            )
        }
    }
}

// The rail takes the screen's start edge: it keeps clear of the system bars there and, lying down,
// of the camera cutout, which the system-bar insets leave out.
private val RailInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

/**
 * For the screen's content beside a [DetailRail]: the rail already keeps clear of the start edge (its
 * system bar and camera cutout), so the content mustn't again. Without it, a phone lying down showed an
 * empty strip, the cutout's width, between the rail and the content.
 */
@Composable
fun Modifier.besideRail(rail: Boolean): Modifier =
    if (rail) consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else this

@Composable
fun DetailRail(tabs: List<DetailTab>, selected: Int, onSelect: (Int) -> Unit) {
    NavigationRail(containerColor = ClubChrome, modifier = Modifier.fillMaxHeight(), windowInsets = RailInsets) {
        Spacer(Modifier.weight(1f))
        tabs.forEachIndexed { i, tab ->
            val isSelected = i == selected
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(i) },
                icon = { TabIcon(tab) },
                label = {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = ChromeAccent,
                    selectedTextColor = ChromeAccent,
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f),
                    indicatorColor = ChromeAccent.copy(alpha = 0.15f)
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
