package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.model.AgeGroup

// Training's access messages and scope (design 39m); who may do what is each server's AccessPolicy.

internal const val MANAGE_TRAINING_MESSAGE = "Only coordinators, or this age group's staff the club allows, can manage its training"
internal const val RATE_TRAINING_MESSAGE = "Only this age group's staff can rate its training"

internal val AgeGroup.scope: GameScope get() = GameScope(clubId, ageGroupId = id)
