package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

/** Body of `POST /v1/fcm-tokens`. The caller's userId always comes from their own JWT, never here. */
@Serializable
data class RegisterFcmTokenRequest(val token: String)

/** Body of `DELETE /v1/fcm-tokens`, sent by the client as part of its own logout flow. */
@Serializable
data class RemoveFcmTokenRequest(val token: String)
