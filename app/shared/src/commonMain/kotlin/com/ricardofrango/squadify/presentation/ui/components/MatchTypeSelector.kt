package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.ic_match_championship
import squadify.app.shared.generated.resources.ic_match_friendly
import squadify.app.shared.generated.resources.ic_match_tournament
import squadify.app.shared.generated.resources.label_match_type_championship
import squadify.app.shared.generated.resources.label_match_type_championship_desc
import squadify.app.shared.generated.resources.label_match_type_friendly
import squadify.app.shared.generated.resources.label_match_type_friendly_desc
import squadify.app.shared.generated.resources.label_match_type_tournament
import squadify.app.shared.generated.resources.label_match_type_tournament_desc
import squadify.app.shared.generated.resources.msg_match_type_hint
import squadify.app.shared.generated.resources.msg_match_type_required

/** The localized name of a [MatchType] -- shared by every screen that shows or picks one. */
fun MatchType.labelRes(): StringResource = when (this) {
    MatchType.FRIENDLY -> Res.string.label_match_type_friendly
    MatchType.CHAMPIONSHIP -> Res.string.label_match_type_championship
    MatchType.TOURNAMENT -> Res.string.label_match_type_tournament
}

/** The one-line explanation shown under each option in the selector (design 28). */
fun MatchType.descriptionRes(): StringResource = when (this) {
    MatchType.FRIENDLY -> Res.string.label_match_type_friendly_desc
    MatchType.CHAMPIONSHIP -> Res.string.label_match_type_championship_desc
    MatchType.TOURNAMENT -> Res.string.label_match_type_tournament_desc
}

/** People for a friendly, a fixture list for a championship, a trophy for a tournament. */
fun MatchType.iconRes(): DrawableResource = when (this) {
    MatchType.FRIENDLY -> Res.drawable.ic_match_friendly
    MatchType.CHAMPIONSHIP -> Res.drawable.ic_match_championship
    MatchType.TOURNAMENT -> Res.drawable.ic_match_tournament
}

private val ErrorRed = Color(0xFFC62828)

/**
 * Friendly / Championship / Tournament as a vertical radio group (design 28): each option is a
 * full-width row with its icon, name and own description, so long labels in any language just add
 * height. [selected] null means nothing picked yet. [error] paints every row red -- the user tried to
 * schedule without a type. With [showHint], a line under the rows explains the choice (the selected
 * option's description once one is picked, or the error).
 */
@Composable
fun MatchTypeSelector(
    selected: MatchType?,
    onSelected: (MatchType) -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    showHint: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MatchType.entries.forEach { type ->
                MatchTypeOption(type, isSelected = type == selected, error = error && selected == null) { onSelected(type) }
            }
        }
        if (showHint) {
            Spacer(Modifier.height(9.dp))
            val showError = error && selected == null
            Text(
                when {
                    showError -> stringResource(Res.string.msg_match_type_required)
                    selected != null -> stringResource(selected.descriptionRes())
                    else -> stringResource(Res.string.msg_match_type_hint)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (showError) FontWeight.Medium else FontWeight.Normal),
                color = when {
                    showError -> ErrorRed
                    selected != null -> DeepCarbon.copy(alpha = 0.8f)
                    else -> DeepCarbon.copy(alpha = 0.55f)
                }
            )
        }
    }
}

@Composable
private fun MatchTypeOption(type: MatchType, isSelected: Boolean, error: Boolean, onClick: () -> Unit) {
    val name = stringResource(type.labelRes())
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { role = Role.RadioButton; this.selected = isSelected },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PitchGreen.copy(alpha = 0.07f) else Color.White,
        border = BorderStroke(
            1.5.dp,
            when {
                isSelected -> PitchGreen
                error -> ErrorRed
                else -> CarbonGray.copy(alpha = 0.16f)
            }
        )
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(type.iconRes()), contentDescription = null, tint = PitchGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                Text(
                    stringResource(type.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            RadioDot(isSelected)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(2.dp, if (selected) PitchGreen else Color(0xFFBDBDBD), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(10.dp).background(PitchGreen, CircleShape))
    }
}

/**
 * A small icon + name chip for a match's type, used in the dark Match Details header (design 29a).
 * [muted] is the cancelled look: the chip stays readable but recedes with the rest of the band.
 */
@Composable
fun MatchTypeChip(type: MatchType, muted: Boolean = false) {
    val fg = if (muted) Color.White.copy(alpha = 0.55f) else Color.White
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = if (muted) 0.08f else 0.12f)) {
        TypeLabel(
            type,
            iconTint = if (muted) fg else Color(0xFF4CAF50),
            textColor = fg,
            iconSize = 12.dp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * The quiet grey tag a match list row shows after the opponent (design 29f). Only the exceptions are
 * tagged -- see [MatchType.isTaggedInLists].
 */
@Composable
fun MatchTypeTag(type: MatchType) {
    val grey = Color(0xFF424242)
    Surface(shape = RoundedCornerShape(6.dp), color = CarbonGray.copy(alpha = 0.06f)) {
        TypeLabel(type, iconTint = grey, textColor = grey, iconSize = 11.dp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** Championship is the bulk of a season, so lists only tag friendlies and tournaments. */
val MatchType.isTaggedInLists: Boolean get() = this != MatchType.CHAMPIONSHIP

@Composable
private fun TypeLabel(type: MatchType, iconTint: Color, textColor: Color, iconSize: Dp, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(type.iconRes()), contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(type.labelRes()),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = textColor,
            maxLines = 1
        )
    }
}

@Preview
@Composable
private fun MatchTypeSelectorPreview() = SquadifyTheme {
    Column(Modifier.background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MatchTypeSelector(selected = null, onSelected = {})
        MatchTypeSelector(selected = MatchType.CHAMPIONSHIP, onSelected = {})
        MatchTypeSelector(selected = null, onSelected = {}, error = true)
    }
}

@Preview
@Composable
private fun MatchTypeChipAndTagPreview() = SquadifyTheme {
    Column(Modifier.background(CarbonGray).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MatchTypeChip(MatchType.CHAMPIONSHIP)
        MatchTypeChip(MatchType.TOURNAMENT, muted = true)
        Box(Modifier.background(Color.White).padding(8.dp)) { MatchTypeTag(MatchType.FRIENDLY) }
    }
}
