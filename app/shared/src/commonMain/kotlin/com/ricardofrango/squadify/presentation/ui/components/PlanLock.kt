package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.domain.config.OwnSpace
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.ageGroupLimit
import com.ricardofrango.squadify.model.peoplePerAgeGroupLimit
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
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

/** Opens the coach app's Plans screen; set with [LocalOwnSpace], for the owner. Null in the clubs app. */
val LocalSeePlans = compositionLocalOf<(() -> Unit)?> { null }

/** The plan's name as sold: the same in every language. */
val SpacePlan.displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * The stats a Free space doesn't have: for the owner of a Free space with See plans, for anyone else (staff,
 * or the owner of another plan reading someone else's Free space) with who can change it.
 */
@Composable
fun StatsNotInPlanMessage(modifier: Modifier = Modifier) {
    val seePlans = statsSeePlans()
    Column(modifier) {
        StatsMessage(stringResource(Res.string.stats_not_in_plan_title), statsNotInPlanBody())
        if (seePlans != null) {
            TextButton(onClick = seePlans) {
                Text(stringResource(Res.string.btn_see_plans), color = PitchGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** See plans on a stats lock: only when the coach's own space is on Free, the one plan without stats. */
@Composable
fun statsSeePlans(): (() -> Unit)? = LocalSeePlans.current?.takeIf { LocalOwnSpace.current?.plan == SpacePlan.FREE }

/** The body under the stats lock's title: what the plans have for the owner, who to ask for anyone else. */
@Composable
fun statsNotInPlanBody(): String =
    stringResource(if (statsSeePlans() != null) Res.string.stats_not_in_plan_body else Res.string.stats_not_in_plan_ask_owner)

/**
 * The owner's age group has no room for more staff on [plan]: none at all on Free, or full on Basic. A
 * grey box with a lock, and See plans.
 */
@Composable
fun StaffNotInPlanNotice(plan: SpacePlan, modifier: Modifier = Modifier) {
    val title: String
    val body: String
    if (plan == SpacePlan.FREE) {
        title = stringResource(Res.string.staff_not_in_plan_free_title)
        body = stringResource(Res.string.staff_not_in_plan_free_body, (SpacePlan.BASIC.peoplePerAgeGroupLimit ?: 1) - 1)
    } else {
        title = stringResource(Res.string.staff_not_in_plan_full_title, plan.displayName)
        body = stringResource(Res.string.staff_not_in_plan_full_body, plan.displayName, plan.peoplePerAgeGroupLimit ?: 0)
    }
    PlanNotice(title, body, LocalSeePlans.current, modifier)
}

/** The own space's age groups left this season on its plan, when [spaceId] is that space and the plan has a limit. */
@Composable
fun ageGroupsLeftOnPlan(spaceId: String): Int? {
    val own = LocalOwnSpace.current?.takeIf { it.spaceId == spaceId } ?: return null
    val limit = own.plan.ageGroupLimit ?: return null
    return (limit - own.ageGroupsThisSeason).coerceAtLeast(0)
}

/** No more age groups this season on [plan]: what each plan has, and See plans. */
@Composable
fun AgeGroupsNotInPlanNotice(plan: SpacePlan, modifier: Modifier = Modifier) {
    val body = if (plan == SpacePlan.FREE) {
        stringResource(Res.string.age_groups_not_in_plan_free_body, SpacePlan.BASIC.ageGroupLimit ?: 0)
    } else {
        stringResource(Res.string.age_groups_not_in_plan_body, plan.displayName, plan.ageGroupLimit ?: 0)
    }
    PlanNotice(stringResource(Res.string.age_groups_not_in_plan_title, plan.displayName), body, LocalSeePlans.current, modifier)
}

/** A plan's limit explained, the way the design draws it: a grey box, a lock, the text, then See plans. */
@Composable
fun PlanNotice(title: String, body: String, onSeePlans: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(CarbonGray.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(Res.drawable.ic_lock), contentDescription = null, tint = CarbonGray.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.75f))
            if (onSeePlans != null) {
                TextButton(onClick = onSeePlans, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(Res.string.btn_see_plans), color = PitchGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
