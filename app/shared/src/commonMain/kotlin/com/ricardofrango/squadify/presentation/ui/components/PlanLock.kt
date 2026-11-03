package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.domain.config.OwnSpace
import com.ricardofrango.squadify.model.PlanDetails
import com.ricardofrango.squadify.model.PlanFeature
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.age_groups_not_in_plan_title
import squadify.app.shared.generated.resources.btn_see_plans
import squadify.app.shared.generated.resources.ic_lock
import squadify.app.shared.generated.resources.plan_age_groups_sentence
import squadify.app.shared.generated.resources.plan_no_limit_sentence
import squadify.app.shared.generated.resources.plan_one_age_group_sentence
import squadify.app.shared.generated.resources.plan_people_sentence
import squadify.app.shared.generated.resources.staff_not_in_plan_alone_body
import squadify.app.shared.generated.resources.staff_not_in_plan_alone_title
import squadify.app.shared.generated.resources.staff_not_in_plan_full_title
import squadify.app.shared.generated.resources.stats_not_in_plan_ask_owner
import squadify.app.shared.generated.resources.stats_not_in_plan_body
import squadify.app.shared.generated.resources.stats_not_in_plan_title
import squadify.app.shared.generated.resources.stats_not_in_plan_title_any

/**
 * Every plan, cheapest first, with what it has, as the coach server decides it (`GET /v1/plans`): the
 * plan boxes' wording comes from it, never from rules in the app. Empty in the clubs app, and until loaded.
 */
val LocalPlans = compositionLocalOf<List<PlanDetails>> { emptyList() }

/** The coach's own space ([OwnSpace]), set by the coach app; null when they own none, and always in the clubs app. */
val LocalOwnSpace = compositionLocalOf<OwnSpace?> { null }

/** The own space's plan when [spaceId] is that space (the shared screens check against the one shown). */
@Composable
fun ownPlanFor(spaceId: String): SpacePlan? = LocalOwnSpace.current?.takeIf { it.spaceId == spaceId }?.plan

/** Opens the coach app's Plans screen, set by the coach app for everyone. Null in the clubs app. */
val LocalSeePlans = compositionLocalOf<(() -> Unit)?> { null }

/** The plan's name as sold: the same in every language. */
val SpacePlan.displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * A plan's limit (REQUIREMENTS §5c), the same box everywhere: white with the sections' grey outline, a
 * padlock, [title] and [message], and See plans under them, in line with the text. See plans opens the
 * coach app's Plans screen ([LocalSeePlans]); the clubs app has no plans and never shows this box.
 */
@Composable
fun PlanLimitBox(title: String, message: String, modifier: Modifier = Modifier) {
    val seePlans = LocalSeePlans.current
    Surface(modifier = modifier.fillMaxWidth(), shape = SectionShape, color = Color.White, border = SectionBorder) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(Res.drawable.ic_lock), contentDescription = null, tint = CarbonGray.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.7f))
                if (seePlans != null) {
                    Text(
                        stringResource(Res.string.btn_see_plans),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PitchGreen,
                        modifier = Modifier.clickable(onClick = seePlans).padding(top = 6.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * The plans after [plan] with more room than it has ([limitOf]: a plan's limit, null for no limit), one
 * sentence each, from the server's plans ([LocalPlans]): "Basic has up to 3 …. Pro has no limit."
 */
@Composable
private fun roomOnOtherPlans(plan: SpacePlan, limitOf: (PlanDetails) -> Int?, sentence: @Composable (PlanDetails, Int) -> String): String {
    val plans = LocalPlans.current
    val index = plans.indexOfFirst { it.plan == plan }
    val own = plans.getOrNull(index)?.let(limitOf) ?: return ""
    return plans.drop(index + 1)
        .filter { other -> limitOf(other)?.let { it > own } ?: true }
        .map { other -> limitOf(other)?.let { sentence(other, it) } ?: stringResource(Res.string.plan_no_limit_sentence, other.plan.displayName) }
        .joinToString(" ")
}

private fun sentences(vararg parts: String) = parts.filter { it.isNotEmpty() }.joinToString(" ")

/**
 * The stats a space's plan doesn't have (the server refused them). The title names the first plan with
 * them; the owner of a space without them reads so, anyone else that the space's owner can change it.
 */
@Composable
fun StatsNotInPlanMessage(modifier: Modifier = Modifier) {
    val plans = LocalPlans.current
    val withStats = plans.firstOrNull { PlanFeature.STATS in it.features }
    val own = LocalOwnSpace.current?.plan?.let { plan -> plans.firstOrNull { it.plan == plan } }
    PlanLimitBox(
        if (withStats != null) stringResource(Res.string.stats_not_in_plan_title, withStats.plan.displayName)
        else stringResource(Res.string.stats_not_in_plan_title_any),
        if (own != null && PlanFeature.STATS !in own.features) stringResource(Res.string.stats_not_in_plan_body, own.plan.displayName)
        else stringResource(Res.string.stats_not_in_plan_ask_owner),
        modifier
    )
}

/**
 * The age group has no room for more staff on [plan] (the server's answer, `AgeGroupDetails.staffFullOnPlan`):
 * the owner alone on a plan for one person, or full; then the plans with more room.
 */
@Composable
fun StaffNotInPlanNotice(plan: SpacePlan, modifier: Modifier = Modifier) {
    val plans = LocalPlans.current
    val limit = plans.firstOrNull { it.plan == plan }?.peoplePerAgeGroupLimit
    val more = roomOnOtherPlans(plan, { it.peoplePerAgeGroupLimit }) { other, people ->
        stringResource(Res.string.plan_people_sentence, other.plan.displayName, people)
    }
    val next = plans.dropWhile { it.plan != plan }.drop(1).firstOrNull { other -> other.peoplePerAgeGroupLimit?.let { limit != null && it > limit } ?: true }
    if (limit == 1) {
        PlanLimitBox(
            if (next != null) stringResource(Res.string.staff_not_in_plan_alone_title, next.plan.displayName)
            else stringResource(Res.string.staff_not_in_plan_full_title, plan.displayName),
            sentences(stringResource(Res.string.staff_not_in_plan_alone_body, plan.displayName), more),
            modifier
        )
    } else {
        PlanLimitBox(
            stringResource(Res.string.staff_not_in_plan_full_title, plan.displayName),
            sentences(limit?.let { stringResource(Res.string.plan_people_sentence, plan.displayName, it) }.orEmpty(), more),
            modifier
        )
    }
}

/** The own space's age groups left this season, as the server counts them, when [spaceId] is that space; null: no limit. */
@Composable
fun ageGroupsLeftOnPlan(spaceId: String): Int? = LocalOwnSpace.current?.takeIf { it.spaceId == spaceId }?.ageGroupsLeft

/** No more age groups this season on [plan]: how many it has, then the plans with more. */
@Composable
fun AgeGroupsNotInPlanNotice(plan: SpacePlan, modifier: Modifier = Modifier) {
    val ageGroupSentence: @Composable (PlanDetails, Int) -> String = { details, count ->
        if (count == 1) stringResource(Res.string.plan_one_age_group_sentence, details.plan.displayName)
        else stringResource(Res.string.plan_age_groups_sentence, details.plan.displayName, count)
    }
    val own = LocalPlans.current.firstOrNull { it.plan == plan }
    PlanLimitBox(
        stringResource(Res.string.age_groups_not_in_plan_title, plan.displayName),
        sentences(
            own?.ageGroupLimit?.let { ageGroupSentence(own, it) }.orEmpty(),
            roomOnOtherPlans(plan, { it.ageGroupLimit }, ageGroupSentence)
        ),
        modifier
    )
}

/** The server's plans for the previews: one person and one age group, three of each, no limits. */
val previewPlans = listOf(
    PlanDetails(SpacePlan.FREE, listOf(PlanFeature.LIVE_GAME, PlanFeature.CALENDAR, PlanFeature.HISTORY), 1, 1),
    PlanDetails(SpacePlan.BASIC, listOf(PlanFeature.LIVE_GAME, PlanFeature.CALENDAR, PlanFeature.HISTORY, PlanFeature.STATS), 3, 3),
    PlanDetails(SpacePlan.PRO, listOf(PlanFeature.LIVE_GAME, PlanFeature.CALENDAR, PlanFeature.HISTORY, PlanFeature.STATS, PlanFeature.FOLLOWING_LIVE, PlanFeature.TRAINING))
)

@Preview
@Composable
private fun PlanLimitBoxPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalSeePlans provides {}, LocalPlans provides previewPlans, LocalOwnSpace provides OwnSpace("s", SpacePlan.FREE, 1, 0)) {
            Column(Modifier.background(ScreenBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsNotInPlanMessage()
                StaffNotInPlanNotice(SpacePlan.FREE)
                AgeGroupsNotInPlanNotice(SpacePlan.BASIC)
            }
        }
    }
}
