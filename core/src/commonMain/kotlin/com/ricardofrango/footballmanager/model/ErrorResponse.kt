package com.ricardofrango.footballmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
    val code: String? = null
)
