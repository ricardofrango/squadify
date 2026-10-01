package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.label_match_type_championship
import squadify.app.shared.generated.resources.label_match_type_friendly
import squadify.app.shared.generated.resources.label_match_type_tournament

/** The localized name of a [MatchType] -- shared by every screen that shows or picks one. */
fun MatchType.labelRes(): StringResource = when (this) {
    MatchType.FRIENDLY -> Res.string.label_match_type_friendly
    MatchType.CHAMPIONSHIP -> Res.string.label_match_type_championship
    MatchType.TOURNAMENT -> Res.string.label_match_type_tournament
}

/**
 * Friendly / Championship / Tournament as three equal options in a row. [selected] null means nothing
 * picked yet (a new match must be typified before it can be scheduled).
 */
@Composable
fun MatchTypeSelector(selected: MatchType?, onSelected: (MatchType) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatchType.entries.forEach { type ->
            val isSelected = type == selected
            Surface(
                onClick = { onSelected(type) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton; this.selected = isSelected },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) PitchGreen else Color.White,
                border = BorderStroke(1.dp, if (isSelected) PitchGreen else CarbonGray.copy(alpha = 0.2f))
            ) {
                Box(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(type.labelRes()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else DeepCarbon,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MatchTypeSelectorPreview() = SquadifyTheme {
    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MatchTypeSelector(selected = null, onSelected = {})
        MatchTypeSelector(selected = MatchType.CHAMPIONSHIP, onSelected = {})
    }
}
