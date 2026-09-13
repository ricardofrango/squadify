package com.ricardofrango.squadify.exception

open class SquadifyException(
    override val message: String,
    val code: String
) : Exception(message)

class UnauthorizedException(message: String = "Unauthorized") : 
    SquadifyException(message, "AUTH_001")

class ForbiddenException(message: String = "Access Denied") : 
    SquadifyException(message, "AUTH_002")

class ValidationException(message: String) : 
    SquadifyException(message, "VAL_001")

class ConflictException(message: String) : 
    SquadifyException(message, "DATA_001")

class NotFoundException(message: String) : 
    SquadifyException(message, "DATA_003")

class DatabaseException(message: String) : 
    SquadifyException(message, "DATA_002")
