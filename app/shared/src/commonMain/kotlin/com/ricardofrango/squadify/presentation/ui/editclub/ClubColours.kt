package com.ricardofrango.squadify.presentation.ui.editclub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.ClubTheme
import com.ricardofrango.squadify.model.ClubThemeProblem
import com.ricardofrango.squadify.model.ClubThemeRules
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.hexColor
import com.ricardofrango.squadify.presentation.ui.theme.rgbColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_choose_club_colours
import squadify.app.shared.generated.resources.btn_remove_colours
import squadify.app.shared.generated.resources.btn_save
import squadify.app.shared.generated.resources.btn_swap_colours
import squadify.app.shared.generated.resources.btn_use_colour
import squadify.app.shared.generated.resources.ic_calendar
import squadify.app.shared.generated.resources.ic_home
import squadify.app.shared.generated.resources.ic_teams
import squadify.app.shared.generated.resources.label_club_colours_section
import squadify.app.shared.generated.resources.label_colour_custom
import squadify.app.shared.generated.resources.label_colour_hex
import squadify.app.shared.generated.resources.label_colour_hue
import squadify.app.shared.generated.resources.label_colour_lightness
import squadify.app.shared.generated.resources.label_colour_preview
import squadify.app.shared.generated.resources.label_colour_saturation
import squadify.app.shared.generated.resources.label_colour_suggested
import squadify.app.shared.generated.resources.label_main_colour
import squadify.app.shared.generated.resources.label_second_colour
import squadify.app.shared.generated.resources.msg_club_colours_none
import squadify.app.shared.generated.resources.msg_club_colours_saved_hint
import squadify.app.shared.generated.resources.msg_colour_invalid
import squadify.app.shared.generated.resources.msg_colour_primary_too_light
import squadify.app.shared.generated.resources.msg_colour_secondary_too_close
import squadify.app.shared.generated.resources.msg_main_colour_hint
import squadify.app.shared.generated.resources.msg_second_colour_hint
import squadify.app.shared.generated.resources.tab_home
import squadify.app.shared.generated.resources.tab_matches
import squadify.app.shared.generated.resources.tab_teams
import kotlin.math.abs
import kotlin.math.roundToInt

// Strong colours for the main slot (all take white text) and light ones for the second.
private val MainSwatches = listOf(
    "#E30613", "#B71C1C", "#7B1E2B", "#C2410C", "#00843D", "#1B5E20", "#2E7D32", "#00695C",
    "#0047AB", "#0B2B5B", "#1E40AF", "#0277BD", "#5B2C83", "#111111", "#37474F", "#5D4037"
)
private val SecondSwatches = listOf(
    "#FFFFFF", "#FFF8E1", "#FDE100", "#F2C230", "#87CEEB", "#BBDEFB",
    "#A5D6A7", "#CDDC39", "#F8BBD0", "#E0E0E0", "#FFB74D", "#CFD8DC"
)

// Picked when a club first chooses colours: Squadify's green and white, a valid pair to start from.
private val StarterTheme = ClubTheme("#2E7D32", "#FFFFFF")

private enum class ColourSlot { MAIN, SECOND }

/**
 * Club settings → Details: the club's two colours (§57). A preview of the bars and a button, a tap
 * target per colour that opens the picker, Swap and Remove. Both colours or neither: "Choose club
 * colours" starts from a valid pair, and Remove clears both.
 */
@Composable
internal fun ClubColoursSection(theme: ClubTheme?, problem: ClubThemeProblem?, clubName: String, initials: String, onChange: (ClubTheme?) -> Unit) {
    var editing by remember { mutableStateOf<ColourSlot?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.label_club_colours_section),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = 0.6f)
        )
        if (theme == null) {
            Text(stringResource(Res.string.msg_club_colours_none), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f))
            Surface(
                onClick = { onChange(StarterTheme); editing = ColourSlot.MAIN },
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, PitchGreen)
            ) {
                Text(
                    stringResource(Res.string.btn_choose_club_colours),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreen
                )
            }
        } else {
            val main = hexColor(theme.primaryColor) ?: Color.Gray
            val second = hexColor(theme.secondaryColor) ?: Color.White
            ThemePreview(main, second, clubName, initials)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColourSlotCard(Res.string.label_main_colour, Res.string.msg_main_colour_hint, theme.primaryColor, main, Modifier.weight(1f)) { editing = ColourSlot.MAIN }
                ColourSlotCard(Res.string.label_second_colour, Res.string.msg_second_colour_hint, theme.secondaryColor, second, Modifier.weight(1f)) { editing = ColourSlot.SECOND }
            }
            problem?.let { Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                ?: Text(stringResource(Res.string.msg_club_colours_saved_hint), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(ClubTheme(theme.secondaryColor, theme.primaryColor)) }) {
                    Text("⇄  " + stringResource(Res.string.btn_swap_colours), color = PitchGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onChange(null) }) {
                    Text(stringResource(Res.string.btn_remove_colours), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val slot = editing
    if (slot != null && theme != null) {
        ColourPickerSheet(
            slot = slot,
            theme = theme,
            onDismiss = { editing = null },
            onPick = { hex ->
                onChange(if (slot == ColourSlot.MAIN) theme.copy(primaryColor = hex) else theme.copy(secondaryColor = hex))
                editing = null
            }
        )
    }
}

internal fun ClubThemeProblem.messageRes() = when (this) {
    ClubThemeProblem.INVALID_COLOR -> Res.string.msg_colour_invalid
    ClubThemeProblem.PRIMARY_TOO_LIGHT -> Res.string.msg_colour_primary_too_light
    ClubThemeProblem.SECONDARY_TOO_CLOSE -> Res.string.msg_colour_secondary_too_close
}

/** A small mock of the club's screens: the top bar, a button, a tint, and the tab bar with one selected. */
@Composable
private fun ThemePreview(main: Color, second: Color, clubName: String, initials: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))) {
        Column {
            Row(Modifier.fillMaxWidth().background(main).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(second, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initials.take(3), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = main)
                }
                Spacer(Modifier.width(10.dp))
                Text(clubName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Text(stringResource(Res.string.label_colour_preview).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(main, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(Res.string.btn_save), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                Box(Modifier.background(main.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).border(1.dp, main, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Sub-15", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = main)
                }
            }
            Row(Modifier.fillMaxWidth().background(main).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                PreviewTab(Res.drawable.ic_home, stringResource(Res.string.tab_home), second, selected = true)
                PreviewTab(Res.drawable.ic_teams, stringResource(Res.string.tab_teams), Color.White.copy(alpha = 0.55f), selected = false)
                PreviewTab(Res.drawable.ic_calendar, stringResource(Res.string.tab_matches), Color.White.copy(alpha = 0.55f), selected = false)
            }
        }
    }
}

@Composable
private fun PreviewTab(icon: org.jetbrains.compose.resources.DrawableResource, label: String, color: Color, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 2.dp)
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = color)
    }
}

@Composable
private fun ColourSlotCard(
    label: org.jetbrains.compose.resources.StringResource,
    hint: org.jetbrains.compose.resources.StringResource,
    hex: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.25f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(color, CircleShape).border(1.dp, CarbonGray.copy(alpha = 0.2f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1)
                Text(hex, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = DeepCarbon.copy(alpha = 0.6f))
                Text(stringResource(hint), style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f), maxLines = 2)
            }
        }
    }
}

/**
 * Picks one colour: suggested swatches, or a custom one by hue, saturation and lightness, or by hex
 * code. Swatches that wouldn't work with the other colour are faded, and the rule it breaks shows
 * under the preview -- still pickable, since the user may be about to change the other one too.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ColourPickerSheet(slot: ColourSlot, theme: ClubTheme, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val start = if (slot == ColourSlot.MAIN) theme.primaryColor else theme.secondaryColor
    val startHsl = remember { rgbToHsl(ClubThemeRules.parse(start) ?: 0x2E7D32) }
    var hue by remember { mutableFloatStateOf(startHsl[0]) }
    var saturation by remember { mutableFloatStateOf(startHsl[1]) }
    var lightness by remember { mutableFloatStateOf(startHsl[2]) }
    var hexText by remember { mutableStateOf(ClubThemeRules.normalize(start)) }

    fun setFromHex(hex: String) {
        val rgb = ClubThemeRules.parse(hex) ?: return
        val hsl = rgbToHsl(rgb)
        hue = hsl[0]; saturation = hsl[1]; lightness = hsl[2]
        hexText = ClubThemeRules.toHex(rgb)
    }
    fun setFromSliders() {
        hexText = ClubThemeRules.toHex(hslToRgb(hue, saturation, lightness))
    }
    fun candidate(hex: String) = if (slot == ColourSlot.MAIN) theme.copy(primaryColor = hex) else theme.copy(secondaryColor = hex)

    val valid = ClubThemeRules.isValidColor(hexText)
    val problem = if (valid) ClubThemeRules.problem(candidate(hexText)) else ClubThemeProblem.INVALID_COLOR
    val current = hexColor(hexText)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(if (slot == ColourSlot.MAIN) Res.string.label_main_colour else Res.string.label_second_colour),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon
            )
            // The pair as it would look: main colour bar with the second colour on it.
            val main = if (slot == ColourSlot.MAIN) current else hexColor(theme.primaryColor)
            val second = if (slot == ColourSlot.SECOND) current else hexColor(theme.secondaryColor)
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(main ?: Color.LightGray).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aa", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.background(second ?: Color.LightGray, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(stringResource(Res.string.tab_home), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = main ?: DeepCarbon)
                }
                Spacer(Modifier.weight(1f))
                Text(hexText, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.85f))
            }
            problem?.let { Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            SheetLabel(stringResource(Res.string.label_colour_suggested))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (if (slot == ColourSlot.MAIN) MainSwatches else SecondSwatches).forEach { hex ->
                    val fits = ClubThemeRules.problem(candidate(hex)) == null
                    val selected = hex == hexText
                    Box(
                        Modifier.size(40.dp)
                            .alpha(if (fits) 1f else 0.3f)
                            .clip(CircleShape)
                            .background(hexColor(hex)!!)
                            .border(if (selected) 3.dp else 1.dp, if (selected) DeepCarbon else CarbonGray.copy(alpha = 0.2f), CircleShape)
                            .clickable { setFromHex(hex) }
                    )
                }
            }

            SheetLabel(stringResource(Res.string.label_colour_custom))
            GradientSlider(
                label = stringResource(Res.string.label_colour_hue),
                value = hue, range = 0f..360f,
                colors = (0..6).map { rgbColor(hslToRgb(it * 60f, 1f, 0.5f)) }
            ) { hue = it; setFromSliders() }
            GradientSlider(
                label = stringResource(Res.string.label_colour_saturation),
                value = saturation, range = 0f..1f,
                colors = listOf(rgbColor(hslToRgb(hue, 0f, lightness)), rgbColor(hslToRgb(hue, 1f, lightness)))
            ) { saturation = it; setFromSliders() }
            GradientSlider(
                label = stringResource(Res.string.label_colour_lightness),
                value = lightness, range = 0f..1f,
                colors = listOf(Color.Black, rgbColor(hslToRgb(hue, saturation, 0.5f)), Color.White)
            ) { lightness = it; setFromSliders() }
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    val cleaned = "#" + text.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                    hexText = cleaned
                    if (ClubThemeRules.isValidColor(cleaned)) setFromHex(cleaned)
                },
                label = { Text(stringResource(Res.string.label_colour_hex)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
                    focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                    focusedLabelColor = PitchGreen, unfocusedLabelColor = CarbonGray
                )
            )
            PrimaryLoadingButton(
                text = stringResource(Res.string.btn_use_colour),
                submittingText = stringResource(Res.string.btn_use_colour),
                isLoading = false,
                enabled = valid,
                onClick = { onPick(hexText) }
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.55f))
}

/** A slider drawn over a gradient of what it changes. */
@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DeepCarbon.copy(alpha = 0.7f))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(colors))
                    .border(1.dp, CarbonGray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

/** 0xRRGGBB -> [hue 0..360, saturation 0..1, lightness 0..1]. */
internal fun rgbToHsl(rgb: Int): FloatArray {
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return floatArrayOf(h, s, l)
}

/** [hue 0..360, saturation 0..1, lightness 0..1] -> 0xRRGGBB. */
internal fun hslToRgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
    return (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview
@Composable
private fun ClubColoursNonePreview() = SquadifyTheme {
    Box(Modifier.background(Color.White).padding(24.dp)) {
        ClubColoursSection(theme = null, problem = null, clubName = "Clube Desportivo Ribamar", initials = "CDR", onChange = {})
    }
}

@Preview
@Composable
private fun ClubColoursSetPreview() = SquadifyTheme {
    Box(Modifier.background(Color.White).padding(24.dp)) {
        ClubColoursSection(theme = ClubTheme("#0B2B5B", "#FDE100"), problem = null, clubName = "Clube Desportivo Ribamar", initials = "CDR", onChange = {})
    }
}

@Preview
@Composable
private fun ClubColoursProblemPreview() = SquadifyTheme {
    val theme = ClubTheme("#FDE100", "#FFFFFF")
    Box(Modifier.background(Color.White).padding(24.dp)) {
        ClubColoursSection(theme = theme, problem = ClubThemeRules.problem(theme), clubName = "Clube Desportivo Ribamar", initials = "CDR", onChange = {})
    }
}
