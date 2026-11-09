package com.ricardofrango.squadify.coach.presentation.ui.space

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.AGE_GROUP_CLUB_SEPARATOR
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ageGroupClubName
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo

// The coach app names an age group after the club it plays for, "{club} - Sub N" (the age-group wizard),
// and gives it that club's crest (`AgeGroup.logoUrl`). There's no club of its own: its name is the part of
// the age group's name before " - ".

/** The club an age group plays for: its name's part before " - " ("Povoense - Sub 11"), or null. */
fun clubNameOf(ageGroup: AgeGroup): String? = ageGroupClubName(ageGroup.name)

/** "Povoense - Sub 11" for [clubName] and [ageGroupName] ("Sub 11"). */
fun ageGroupNameFor(clubName: String, ageGroupName: String): String = "${clubName.trim()}$AGE_GROUP_CLUB_SEPARATOR$ageGroupName"

/**
 * A club's initials for its crest placeholder: the first letters of its first two words, leaving out a
 * short prefix like "CF" or "GD" when there's more ("CF Santa Iria" → "SI"), or the first two letters of a
 * single word ("Povoense" → "PO").
 */
fun clubInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val meaningful = words.filter { it.length > 2 }.ifEmpty { words }
    return when {
        meaningful.isEmpty() -> "?"
        meaningful.size == 1 -> meaningful.single().take(2).uppercase()
        else -> meaningful.take(2).joinToString("") { it.first().uppercase() }
    }
}

/** A club's placeholder colour: always the same for the same name, so its age groups match. */
fun clubColor(name: String): Color = CrestColors[(name.trim().lowercase().hashCode() and Int.MAX_VALUE) % CrestColors.size]

private val CrestColors = listOf(
    Color(0xFFC62828), Color(0xFF1565C0), Color(0xFF00695C), Color(0xFF6A1B9A),
    Color(0xFFEF6C00), Color(0xFF4E342E), Color(0xFF283593), Color(0xFFAD1457)
)

/**
 * An age group's crest: its club's image, or the club's initials on the club's colour until it has one
 * (the age group's own name's, when it isn't named after a club).
 */
@Composable
fun AgeGroupCrest(ageGroup: AgeGroup, size: Dp = 40.dp) {
    val club = clubNameOf(ageGroup) ?: ageGroup.name
    ClubCrest(club, ageGroup.logoUrl, size)
}

/** A club's crest by its [name] and image path, as [AgeGroupCrest]. */
@Composable
fun ClubCrest(name: String, logoUrl: String?, size: Dp = 40.dp) {
    ClubLogo(
        logoUrl = logoUrl,
        initials = clubInitials(name),
        size = size,
        shape = RoundedCornerShape(size * 0.22f),
        background = if (logoUrl != null) Color.White else clubColor(name),
        initialsColor = Color.White,
        initialsStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.32f).sp)
    )
}
