package com.ricardofrango.squadify.presentation.ui.components

import com.ricardofrango.squadify.model.PlayerPosition
import org.jetbrains.compose.resources.StringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.label_position_defender
import squadify.app.shared.generated.resources.label_position_goalkeeper
import squadify.app.shared.generated.resources.label_position_midfield
import squadify.app.shared.generated.resources.label_position_striker
import squadify.app.shared.generated.resources.label_position_winger

/** The localized display name for a [PlayerPosition] -- shared by every screen that shows one. */
fun PlayerPosition.labelRes(): StringResource = when (this) {
    PlayerPosition.GOALKEEPER -> Res.string.label_position_goalkeeper
    PlayerPosition.DEFENDER -> Res.string.label_position_defender
    PlayerPosition.MIDFIELD -> Res.string.label_position_midfield
    PlayerPosition.WINGER -> Res.string.label_position_winger
    PlayerPosition.STRIKER -> Res.string.label_position_striker
}
