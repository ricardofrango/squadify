package com.ricardofrango.squadify.security

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

val ApplicationCall.userId: String?
    get() = principal<JWTPrincipal>()?.payload?.subject
