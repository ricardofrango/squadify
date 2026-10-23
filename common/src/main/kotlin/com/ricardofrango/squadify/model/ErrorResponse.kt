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
