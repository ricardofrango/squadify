package com.sportssoftw.squadcall.presentation.ui.components

import com.sportssoftw.squadcall.model.PlayerPosition
import org.jetbrains.compose.resources.StringResource
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.label_position_defender
import squadcall.app.shared.generated.resources.label_position_goalkeeper
import squadcall.app.shared.generated.resources.label_position_midfield
import squadcall.app.shared.generated.resources.label_position_striker
import squadcall.app.shared.generated.resources.label_position_winger

/** The localized display name for a [PlayerPosition] -- shared by every screen that shows one. */
fun PlayerPosition.labelRes(): StringResource = when (this) {
    PlayerPosition.GOALKEEPER -> Res.string.label_position_goalkeeper
    PlayerPosition.DEFENDER -> Res.string.label_position_defender
    PlayerPosition.MIDFIELD -> Res.string.label_position_midfield
    PlayerPosition.WINGER -> Res.string.label_position_winger
    PlayerPosition.STRIKER -> Res.string.label_position_striker
}
