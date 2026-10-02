package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.halves_one
import squadify.app.shared.generated.resources.halves_one_hint
import squadify.app.shared.generated.resources.halves_two

/**
 * "2 halves · 1 half" as one segmented control, for Schedule match and Edit match. Two halves is the
 * default; one half is a single period with no half time (the live console then only offers Full time).
 */
@Composable
fun HalvesSelector(halves: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, CarbonGray.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
        ) {
            listOf(2 to Res.string.halves_two, 1 to Res.string.halves_one).forEach { (value, label) ->
                val isSelected = value == halves
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .background(if (isSelected) CarbonGray else Color.White)
                        .semantics { role = Role.RadioButton; this.selected = isSelected }
                        .clickable { onSelected(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else DeepCarbon
                    )
                }
            }
        }
        if (halves == 1) {
            Text(
                stringResource(Res.string.halves_one_hint),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.55f)
            )
        }
    }
}
