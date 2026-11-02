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
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.ageGroupLimit
import com.ricardofrango.squadify.model.peoplePerAgeGroupLimit
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.age_groups_not_in_plan_body
import squadify.app.shared.generated.resources.age_groups_not_in_plan_free_body
import squadify.app.shared.generated.resources.age_groups_not_in_plan_title
import squadify.app.shared.generated.resources.btn_see_plans
import squadify.app.shared.generated.resources.ic_lock
import squadify.app.shared.generated.resources.staff_not_in_plan_free_body
import squadify.app.shared.generated.resources.staff_not_in_plan_free_title
import squadify.app.shared.generated.resources.staff_not_in_plan_full_body
import squadify.app.shared.generated.resources.staff_not_in_plan_full_title
import squadify.app.shared.generated.resources.stats_not_in_plan_ask_owner
import squadify.app.shared.generated.resources.stats_not_in_plan_body
import squadify.app.shared.generated.resources.stats_not_in_plan_title

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
 * The stats a Free space doesn't have. The owner of a Free space reads that the plans add them, anyone
 * else that the space's owner can change the plan.
 */
@Composable
fun StatsNotInPlanMessage(modifier: Modifier = Modifier) {
    val ownerOfFree = LocalOwnSpace.current?.plan == SpacePlan.FREE
    PlanLimitBox(
        stringResource(Res.string.stats_not_in_plan_title),
        stringResource(if (ownerOfFree) Res.string.stats_not_in_plan_body else Res.string.stats_not_in_plan_ask_owner),
        modifier
    )
}

/** The owner's age group has no room for more staff on [plan]: none at all on Free, or full on Basic. */
@Composable
fun StaffNotInPlanNotice(plan: SpacePlan, modifier: Modifier = Modifier) {
    if (plan == SpacePlan.FREE) {
        PlanLimitBox(
            stringResource(Res.string.staff_not_in_plan_free_title),
            stringResource(Res.string.staff_not_in_plan_free_body, (SpacePlan.BASIC.peoplePerAgeGroupLimit ?: 1) - 1),
            modifier
        )
    } else {
        PlanLimitBox(
            stringResource(Res.string.staff_not_in_plan_full_title, plan.displayName),
            stringResource(Res.string.staff_not_in_plan_full_body, plan.displayName, plan.peoplePerAgeGroupLimit ?: 0),
            modifier
        )
    }
}

/** The own space's age groups left this season on its plan, when [spaceId] is that space and the plan has a limit. */
@Composable
fun ageGroupsLeftOnPlan(spaceId: String): Int? {
    val own = LocalOwnSpace.current?.takeIf { it.spaceId == spaceId } ?: return null
    val limit = own.plan.ageGroupLimit ?: return null
    return (limit - own.ageGroupsThisSeason).coerceAtLeast(0)
}

/** No more age groups this season on [plan]: what each plan has. */
@Composable
fun AgeGroupsNotInPlanNotice(plan: SpacePlan, modifier: Modifier = Modifier) {
    PlanLimitBox(
        stringResource(Res.string.age_groups_not_in_plan_title, plan.displayName),
        if (plan == SpacePlan.FREE) stringResource(Res.string.age_groups_not_in_plan_free_body, SpacePlan.BASIC.ageGroupLimit ?: 0)
        else stringResource(Res.string.age_groups_not_in_plan_body, plan.displayName, plan.ageGroupLimit ?: 0),
        modifier
    )
}

@Preview
@Composable
private fun PlanLimitBoxPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalSeePlans provides {}, LocalOwnSpace provides OwnSpace("s", SpacePlan.FREE, 1)) {
            Column(Modifier.background(ScreenBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsNotInPlanMessage()
                StaffNotInPlanNotice(SpacePlan.FREE)
                AgeGroupsNotInPlanNotice(SpacePlan.BASIC)
            }
        }
    }
}
