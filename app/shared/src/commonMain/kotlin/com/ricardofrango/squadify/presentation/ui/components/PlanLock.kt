package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_see_plans
import squadify.app.shared.generated.resources.stats_not_in_plan_ask_owner
import squadify.app.shared.generated.resources.stats_not_in_plan_body
import squadify.app.shared.generated.resources.stats_not_in_plan_title

/**
 * Opens the coach app's Plans screen from a "not in your plan" message: set by the coach app for the
 * owner of a space on Free (REQUIREMENTS §5c). Null for staff, who are told the owner can change the
 * plan, and in the clubs app, which has no plans.
 */
val LocalSeePlans = compositionLocalOf<(() -> Unit)?> { null }

/** The stats a Free space doesn't have: for its owner with "See plans", for staff with who can change it. */
@Composable
fun StatsNotInPlanMessage(modifier: Modifier = Modifier) {
    val seePlans = LocalSeePlans.current
    Column(modifier) {
        StatsMessage(stringResource(Res.string.stats_not_in_plan_title), statsNotInPlanBody())
        if (seePlans != null) {
            TextButton(onClick = seePlans) {
                Text(stringResource(Res.string.btn_see_plans), color = PitchGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The body under the title: what the plans have for the owner, who to ask for staff. */
@Composable
fun statsNotInPlanBody(): String =
    stringResource(if (LocalSeePlans.current != null) Res.string.stats_not_in_plan_body else Res.string.stats_not_in_plan_ask_owner)
