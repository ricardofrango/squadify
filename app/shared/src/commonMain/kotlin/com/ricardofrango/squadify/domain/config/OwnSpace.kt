package com.ricardofrango.squadify.domain.config

import com.ricardofrango.squadify.model.SpacePlan

/**
 * The space the signed-in coach owns, for the shared screens' plan messages (REQUIREMENTS §5c): its
 * [plan] and how many age groups it has this season. The coach app keeps it; the clubs app has no plans.
 */
data class OwnSpace(val spaceId: String, val plan: SpacePlan, val ageGroupsThisSeason: Int)
