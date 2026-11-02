package com.ricardofrango.squadify.coach.presentation.ui.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.displayName
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.presentation.ui.components.SectionBorder
import com.ricardofrango.squadify.presentation.ui.components.SectionShape
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.plans_basic_age_groups
import squadify.app.coach.generated.resources.plans_basic_live_scores
import squadify.app.coach.generated.resources.plans_basic_people
import squadify.app.coach.generated.resources.plans_basic_stats
import squadify.app.coach.generated.resources.plans_free_age_groups
import squadify.app.coach.generated.resources.plans_free_calendar
import squadify.app.coach.generated.resources.plans_free_history
import squadify.app.coach.generated.resources.plans_free_live_game
import squadify.app.coach.generated.resources.plans_free_people
import squadify.app.coach.generated.resources.plans_intro_basic
import squadify.app.coach.generated.resources.plans_intro_free
import squadify.app.coach.generated.resources.plans_intro_pro
import squadify.app.coach.generated.resources.plans_pro_age_groups
import squadify.app.coach.generated.resources.plans_pro_people
import squadify.app.coach.generated.resources.plans_pro_training
import squadify.app.coach.generated.resources.plans_title
import squadify.app.coach.generated.resources.plans_your_plan

/** What each plan has, the way the Plans screen lists it: an intro line, then what it adds. */
private class PlanCard(val plan: SpacePlan, val intro: StringResource, val items: List<StringResource>)

private val planCards = listOf(
    PlanCard(
        SpacePlan.FREE,
        Res.string.plans_intro_free,
        listOf(Res.string.plans_free_live_game, Res.string.plans_free_calendar, Res.string.plans_free_history, Res.string.plans_free_age_groups, Res.string.plans_free_people)
    ),
    PlanCard(
        SpacePlan.BASIC,
        Res.string.plans_intro_basic,
        listOf(Res.string.plans_basic_stats, Res.string.plans_basic_live_scores, Res.string.plans_basic_age_groups, Res.string.plans_basic_people)
    ),
    PlanCard(
        SpacePlan.PRO,
        Res.string.plans_intro_pro,
        listOf(Res.string.plans_pro_training, Res.string.plans_pro_age_groups, Res.string.plans_pro_people)
    )
)

/**
 * The three plans, each listing what it adds to the one before, with the space's own [plan] outlined and
 * tagged "Your plan" (REQUIREMENTS §5c). Only to read: no prices and no buying until payments, a platform
 * admin sets the plan.
 */
@Composable
fun PlansScreen(plan: SpacePlan, onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = ScreenBackground,
        topBar = { SquadifyTopBar(title = stringResource(Res.string.plans_title), onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            planCards.forEach { PlanCardView(it, isCurrent = it.plan == plan) }
        }
    }
}

@Composable
private fun PlanCardView(card: PlanCard, isCurrent: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape,
        color = Color.White,
        border = if (isCurrent) BorderStroke(2.dp, PitchGreen) else SectionBorder
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.plan.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon,
                    modifier = Modifier.weight(1f)
                )
                if (isCurrent) {
                    Surface(shape = RoundedCornerShape(12.dp), color = PitchGreen) {
                        Text(
                            stringResource(Res.string.plans_your_plan),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Text(stringResource(card.intro), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
            card.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✓", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen)
                    Text(stringResource(item), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PlansFreePreview() {
    SquadifyTheme { PlansScreen(SpacePlan.FREE, onNavigateBack = {}) }
}

@Preview
@Composable
private fun PlansBasicPreview() {
    SquadifyTheme { PlansScreen(SpacePlan.BASIC, onNavigateBack = {}) }
}

@Preview(widthDp = 320)
@Composable
private fun PlansProSmallPreview() {
    SquadifyTheme { PlansScreen(SpacePlan.PRO, onNavigateBack = {}) }
}
