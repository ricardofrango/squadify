package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

/** A small count picker, − [value] +, between [min] and [max] (e.g. how many teams an age group gets). */
@Composable
fun CountStepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(modifier.border(BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f)), shape), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Text("−", fontWeight = FontWeight.Bold, color = if (value > min) PitchGreen else CarbonGray.copy(alpha = 0.3f))
        }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
        TextButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Text("+", fontWeight = FontWeight.Bold, color = if (value < max) PitchGreen else CarbonGray.copy(alpha = 0.3f))
        }
    }
}

@Preview
@Composable
private fun CountStepperPreview() {
    SquadifyTheme { CountStepper(value = 3, onChange = {}, min = 1, max = 5) }
}
