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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.PlanDetails
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
import org.jetbrains.compose.resources.stringResource
import squadify.app.coach.generated.resources.Res
import squadify.app.shared.generated.resources.Res as SharedRes
import squadify.app.shared.generated.resources.btn_retry
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import kotlinx.coroutines.launch
import squadify.app.coach.generated.resources.btn_plans_subscribe
import squadify.app.coach.generated.resources.btn_plans_subscribe_to
import squadify.app.coach.generated.resources.plans_request_failed
import squadify.app.coach.generated.resources.btn_plans_subscribing
import squadify.app.coach.generated.resources.plans_requested
import squadify.app.coach.generated.resources.plans_age_groups_no_limit
import squadify.app.coach.generated.resources.plans_age_groups_one
import squadify.app.coach.generated.resources.plans_age_groups_up_to
import squadify.app.coach.generated.resources.plans_intro_first
import squadify.app.coach.generated.resources.plans_intro_more
import squadify.app.coach.generated.resources.plans_load_failed
import squadify.app.coach.generated.resources.plans_people_no_limit
import squadify.app.coach.generated.resources.plans_people_only_you
import squadify.app.coach.generated.resources.plans_people_up_to
import squadify.app.coach.generated.resources.plans_title
import squadify.app.coach.generated.resources.plans_your_plan

/**
 * The plans as the coach server sends them ([plans], cheapest first, with their names and features
 * written in the app's language), each listing what it adds to the one before and its limits, with the
 * coach's own ([currentPlan], from the server too) tagged "Your plan" (none for a coach without a space of
 * their own) (REQUIREMENTS §5c). Nothing about the plans is decided here, so a plan added or changed on the
 * server shows as it is. Before the plans load, a spinner; [onRetry] when they couldn't.
 *
 * No prices and no buying until payments: the owner taps a plan to choose it (outlined in green; their
 * own, or the one they asked for, to start with), and Subscribe, off until they choose another, asks the
 * server for it with [onRequestPlan] (null for a coach without a space of their own: nothing to tap, no
 * button), whether it went; a platform admin then sets it. The plan asked for ([requestedPlan]) shows above
 * the button until then.
 */
@Composable
fun PlansScreen(
    currentPlan: SpacePlan?,
    requestedPlan: SpacePlan?,
    plans: List<PlanDetails>,
    onRetry: (() -> Unit)?,
    onRequestPlan: (suspend (SpacePlan) -> Boolean)?,
    onNavigateBack: () -> Unit
) {
    var selected by remember(currentPlan, requestedPlan) { mutableStateOf(requestedPlan ?: currentPlan) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Another plan than theirs, and not the one already asked for.
    val chosen = plans.firstOrNull { it.plan == selected && it.plan != currentPlan && it.plan != requestedPlan }
    Scaffold(
        containerColor = ScreenBackground,
        topBar = { SquadifyTopBar(title = stringResource(Res.string.plans_title), onNavigateBack = onNavigateBack) },
        bottomBar = {
            if (onRequestPlan != null && plans.isNotEmpty()) {
                PinnedBottomBar {
                    val note = when {
                        failed -> stringResource(Res.string.plans_request_failed)
                        else -> plans.firstOrNull { it.plan == requestedPlan }?.let { stringResource(Res.string.plans_requested, it.name) }
                    }
                    note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (failed) AlertRed else DeepCarbon.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PrimaryLoadingButton(
                        text = chosen?.let { stringResource(Res.string.btn_plans_subscribe_to, it.name) } ?: stringResource(Res.string.btn_plans_subscribe),
                        submittingText = stringResource(Res.string.btn_plans_subscribing),
                        isLoading = sending,
                        enabled = chosen != null,
                        onClick = {
                            val plan = chosen?.plan ?: return@PrimaryLoadingButton
                            scope.launch {
                                sending = true
                                failed = false
                                failed = !onRequestPlan(plan)
                                sending = false
                            }
                        }
                    )
                }
            }
        }
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
                plans.forEachIndexed { index, details ->
                    PlanCardView(
                        details,
                        plans.getOrNull(index - 1),
                        isCurrent = details.plan == currentPlan,
                        isSelected = details.plan == selected,
                        onClick = if (onRequestPlan != null && !sending) ({ selected = details.plan; failed = false }) else null
                    )
                }
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
private fun PlanCardView(details: PlanDetails, previous: PlanDetails?, isCurrent: Boolean, isSelected: Boolean, onClick: (() -> Unit)?) {
    // What it adds to the plan before, then its limits.
    val features = details.features.filter { feature -> previous == null || !previous.has(feature.key) }.map { it.text }
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
        modifier = Modifier
            .fillMaxWidth()
            // Clipped first, so the ripple keeps to the card's rounded corners.
            .clip(SectionShape)
            .then(if (onClick != null) Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick) else Modifier),
        shape = SectionShape,
        color = Color.White,
        border = if (isSelected) BorderStroke(2.dp, PitchGreen) else SectionBorder
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    details.name,
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
                if (previous == null) stringResource(Res.string.plans_intro_first) else stringResource(Res.string.plans_intro_more, previous.name),
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
    SquadifyTheme { PlansScreen(SpacePlan("FREE"), requestedPlan = null, previewPlans, onRetry = null, onRequestPlan = { true }, onNavigateBack = {}) }
}

@Preview
@Composable
private fun PlansBasicPreview() {
    SquadifyTheme { PlansScreen(SpacePlan("BASIC"), requestedPlan = SpacePlan("PRO"), previewPlans, onRetry = null, onRequestPlan = { true }, onNavigateBack = {}) }
}

@Preview
@Composable
private fun PlansStaffOnlyPreview() {
    SquadifyTheme { PlansScreen(currentPlan = null, requestedPlan = null, previewPlans, onRetry = null, onRequestPlan = null, onNavigateBack = {}) }
}

@Preview(widthDp = 320)
@Composable
private fun PlansProSmallPreview() {
    SquadifyTheme { PlansScreen(SpacePlan("PRO"), requestedPlan = null, previewPlans, onRetry = null, onRequestPlan = { true }, onNavigateBack = {}) }
}
