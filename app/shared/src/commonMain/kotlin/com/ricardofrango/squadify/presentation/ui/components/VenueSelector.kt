package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.ourSideFirst
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.venue_away
import squadify.app.shared.generated.resources.venue_home
import squadify.app.shared.generated.resources.venue_neutral

fun MatchVenue.labelRes(): StringResource = when (this) {
    MatchVenue.HOME -> Res.string.venue_home
    MatchVenue.AWAY -> Res.string.venue_away
    MatchVenue.NEUTRAL -> Res.string.venue_neutral
}

/**
 * Home / Away / Neutral, side by side like the halves picker. [selected] null shows none picked: a new
 * match has no default. [error] paints the border red (scheduling was tapped without one).
 */
@Composable
fun VenueSelector(selected: MatchVenue?, onSelected: (MatchVenue) -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, if (error) MaterialTheme.colorScheme.error else CarbonGray.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
    ) {
        MatchVenue.entries.forEach { venue ->
            val isSelected = venue == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (isSelected) ClubChrome else Color.White)
                    .semantics { role = Role.RadioButton; this.selected = isSelected }
                    .clickable { onSelected(venue) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(venue.labelRes()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color.White else DeepCarbon
                )
            }
        }
    }
}

/**
 * Where a match at [venue] is played, to prefill its location: the club's ground at home
 * ([clubLocation]), the opponent's away, nothing on neutral ground. The user can still edit it.
 */
fun venueLocation(venue: MatchVenue, clubLocation: String?, opponent: Opponent?): String = when (venue) {
    MatchVenue.HOME -> clubLocation.orEmpty()
    MatchVenue.AWAY -> opponent?.let { it.stadiumAddress ?: it.stadiumName }.orEmpty()
    MatchVenue.NEUTRAL -> ""
}

/** A score as football writes it, home side first: "2–1" ours at home or neutral, theirs first away. */
fun MatchScore.homeFirst(venue: MatchVenue, separator: String = "–"): String =
    if (venue.ourSideFirst()) "$ourScore$separator$opponentScore" else "$opponentScore$separator$ourScore"
