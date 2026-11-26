package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.Club

/** A match's own team size (Match.teamSize) must be one its club's sport is played with. */
internal fun requireMatchTeamSize(teamSize: Int, club: Club) {
    if (teamSize !in club.sport.teamSizes) {
        throw ValidationException("Team size $teamSize is not valid for ${club.sport}")
    }
}
