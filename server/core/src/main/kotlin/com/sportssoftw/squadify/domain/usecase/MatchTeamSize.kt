package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.Club

/** A match's own team size (Match.teamSize) must be one its club's sport is played with. */
internal fun requireMatchTeamSize(teamSize: Int, club: Club) {
    if (teamSize !in club.sport.teamSizes) {
        throw ValidationException("Team size $teamSize is not valid for ${club.sport}")
    }
}
