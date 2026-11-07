package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.isInPlay

/**
 * During the game only the person managing it ([Match.liveManagerUserId]) changes it: the live
 * actions call this after their permission check. Anyone else with the permission takes the game over
 * first (TakeOverLiveMatchUseCase). Before kickoff and after full time it doesn't apply, nor to a game
 * kicked off before it existed (no manager).
 */
fun Match.requireLiveManager(userId: String) {
    if (isInPlay && liveManagerUserId != null && liveManagerUserId != userId) {
        throw ConflictException("Someone else is managing this game. Take it over to make changes")
    }
}

/** During the game, someone other than [userId] manages it. */
fun Match.managedByOtherThan(userId: String): Boolean = isInPlay && liveManagerUserId != null && liveManagerUserId != userId
