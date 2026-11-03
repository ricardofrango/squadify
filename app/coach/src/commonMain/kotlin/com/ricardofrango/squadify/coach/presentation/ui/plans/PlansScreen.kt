package com.ricardofrango.squadify.coach.presentation.ui.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.displayName
import com.ricardofrango.squadify.model.PlanDetails
import com.ricardofrango.squadify.model.PlanFeature
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.presentation.ui.components.SectionBorder
import com.ricardofrango.squadify.presentation.ui.components.SectionShape
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.previewPlans
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
import squadify.app.shared.generated.resources.Res as SharedRes
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.coach.generated.resources.plans_age_groups_no_limit
import squadify.app.coach.generated.resources.plans_age_groups_one
import squadify.app.coach.generated.resources.plans_age_groups_up_to
import squadify.app.coach.generated.resources.plans_feature_calendar
import squadify.app.coach.generated.resources.plans_feature_following_live
import squadify.app.coach.generated.resources.plans_feature_history
import squadify.app.coach.generated.resources.plans_feature_live_game
import squadify.app.coach.generated.resources.plans_feature_stats
import squadify.app.coach.generated.resources.plans_feature_training
import squadify.app.coach.generated.resources.plans_intro_first
import squadify.app.coach.generated.resources.plans_intro_more
import squadify.app.coach.generated.resources.plans_load_failed
import squadify.app.coach.generated.resources.plans_people_no_limit
import squadify.app.coach.generated.resources.plans_people_only_you
import squadify.app.coach.generated.resources.plans_people_up_to
import squadify.app.coach.generated.resources.plans_title
import squadify.app.coach.generated.resources.plans_your_plan

/** How the Plans screen names a feature the server lists ([PlanFeature]); null for one this app doesn't know. */
private fun featureLabel(feature: String): StringResource? = when (feature) {
    PlanFeature.LIVE_GAME -> Res.string.plans_feature_live_game
    PlanFeature.CALENDAR -> Res.string.plans_feature_calendar
    PlanFeature.HISTORY -> Res.string.plans_feature_history
    PlanFeature.STATS -> Res.string.plans_feature_stats
    PlanFeature.FOLLOWING_LIVE -> Res.string.plans_feature_following_live
    PlanFeature.TRAINING -> Res.string.plans_feature_training
    else -> null
}

/**
 * The plans as the coach server sends them ([plans], cheapest first), each listing what it adds to the
 * one before and its limits, with the space's own [plan] outlined and tagged "Your plan" (none for a
 * coach without a space of their own) (REQUIREMENTS §5c). Nothing about the plans is decided here, so a
 * plan changed on the server shows as it is. Only to read: no prices and no buying until payments, a
 * platform admin sets the plan. Before the plans load, a spinner; [onRetry] when they couldn't.
 */
@Composable
fun PlansScreen(plan: SpacePlan?, plans: List<PlanDetails>, onRetry: (() -> Unit)?, onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = ScreenBackground,
        topBar = { SquadifyTopBar(title = stringResource(Res.string.plans_title), onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        when {
            plans.isNotEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .screenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                plans.forEachIndexed { index, details -> PlanCardView(details, plans.getOrNull(index - 1), isCurrent = details.plan == plan) }
            }
            onRetry != null -> Column(
                Modifier.fillMaxSize().padding(paddingValues).screenContentPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(Res.string.plans_load_failed), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) {
                    Text(stringResource(SharedRes.string.btn_retry), color = PitchGreen, fontWeight = FontWeight.Bold)
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }
        }
    }
}

@Composable
private fun PlanCardView(details: PlanDetails, previous: PlanDetails?, isCurrent: Boolean) {
    // What it adds to the plan before, then its limits.
    val features = details.features.filter { previous == null || it !in previous.features }.mapNotNull { featureLabel(it) }.map { stringResource(it) }
    val ageGroups = when (val limit = details.ageGroupLimit) {
        null -> stringResource(Res.string.plans_age_groups_no_limit)
        1 -> stringResource(Res.string.plans_age_groups_one)
        else -> stringResource(Res.string.plans_age_groups_up_to, limit)
    }
    val people = when (val limit = details.peoplePerAgeGroupLimit) {
        null -> stringResource(Res.string.plans_people_no_limit)
        1 -> stringResource(Res.string.plans_people_only_you)
        else -> stringResource(Res.string.plans_people_up_to, limit)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape,
        color = Color.White,
        border = if (isCurrent) BorderStroke(2.dp, PitchGreen) else SectionBorder
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    details.plan.displayName,
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
            Text(
                if (previous == null) stringResource(Res.string.plans_intro_first) else stringResource(Res.string.plans_intro_more, previous.plan.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = CarbonGray.copy(alpha = 0.7f)
            )
            (features + ageGroups + people).forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✓", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen)
                    Text(item, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PlansFreePreview() {
    SquadifyTheme { PlansScreen(SpacePlan.FREE, previewPlans, onRetry = null, onNavigateBack = {}) }
}

@Preview
@Composable
private fun PlansBasicPreview() {
    SquadifyTheme { PlansScreen(SpacePlan.BASIC, previewPlans, onRetry = null, onNavigateBack = {}) }
}

@Preview(widthDp = 320)
@Composable
private fun PlansProSmallPreview() {
    SquadifyTheme { PlansScreen(SpacePlan.PRO, previewPlans, onRetry = null, onNavigateBack = {}) }
}
