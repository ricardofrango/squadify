package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
    val code: String? = null,
    // For a batch request: which items (0-based, in request order) the error is about -- e.g. the
    // age groups whose birth years clash -- so a screen can mark every one at once.
    val rows: List<Int>? = null
)

/** [ErrorResponse.code] of a 403 for something not on the space's plan (the coach server, REQUIREMENTS §5c). */
const val PLAN_LIMIT_CODE = "PLAN_001"
