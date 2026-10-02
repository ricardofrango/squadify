package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// The tab bar of the Team and Age Group detail screens (designs 32, 33): the club screen's dark
// bottom bar in portrait, a left rail in landscape (a bottom bar costs too much of a short screen).

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
    NavigationBar(containerColor = CarbonGray, tonalElevation = 0.dp, windowInsets = NavigationBarDefaults.windowInsets) {
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
                    selectedIconColor = PitchGreen,
                    selectedTextColor = PitchGreen,
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f),
                    indicatorColor = PitchGreen.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
fun DetailRail(tabs: List<DetailTab>, selected: Int, onSelect: (Int) -> Unit) {
    NavigationRail(containerColor = CarbonGray, modifier = Modifier.fillMaxHeight()) {
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
                    selectedIconColor = PitchGreen,
                    selectedTextColor = PitchGreen,
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f),
                    indicatorColor = PitchGreen.copy(alpha = 0.15f)
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
