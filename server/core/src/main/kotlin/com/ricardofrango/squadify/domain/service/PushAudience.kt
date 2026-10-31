package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository

/**
 * Who gets an age group's notifications, per server (TECHNICAL_DECISIONS §72). Called inside the use
 * cases' transactions; returns user ids.
 */
interface PushAudience {
    /** A game of [ageGroupId] (scheduled, rescheduled, cancelled, live moments) and its new teams. */
    suspend fun forGames(clubId: String, ageGroupId: String): List<String>

    /** A call-up of [ageGroupId] saved ready. */
    suspend fun forCallUps(clubId: String, ageGroupId: String): List<String>
}

/**
 * The clubs server's: games and teams go to the whole club (as always), a ready call-up only to the age
 * group's staff (the developer, 2026-10-31).
 */
class ClubPushAudience(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository
) : PushAudience {
    override suspend fun forGames(clubId: String, ageGroupId: String): List<String> =
        clubRepository.getClubMembers(clubId).map { it.userId }

    override suspend fun forCallUps(clubId: String, ageGroupId: String): List<String> =
        ageGroupRepository.getAgeGroupStaff(ageGroupId).map { it.userId }
}
