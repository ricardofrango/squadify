package com.sportssoftw.squadcall.domain.config

import com.sportssoftw.squadcall.model.SpacePlan

/**
 * The space the signed-in coach owns, for the shared screens' plan messages (REQUIREMENTS §5c): its
 * [plan], how many age groups it has this season and how many more the plan allows ([ageGroupsLeft],
 * the server's count; null: no limit), and whether it can add one now ([canAddAgeGroups], the server's decision). The
 * coach app keeps it; the clubs app has no plans.
 */
data class OwnSpace(
    val spaceId: String,
    val plan: SpacePlan,
    val ageGroupsThisSeason: Int,
    val ageGroupsLeft: Int?,
    val canAddAgeGroups: Boolean
)
