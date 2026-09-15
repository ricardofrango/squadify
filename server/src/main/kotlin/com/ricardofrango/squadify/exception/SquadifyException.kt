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

/** A path/body value that should be a UUID (an entity id) isn't one -- e.g. a stale client holding a
 * deleted resource's id, a typo'd URL, or a malformed request. Distinct from [ValidationException] so
 * callers of `String.toUuid()` (see `server/.../util/UuidParsing.kt`) get a specific, known exception
 * instead of a raw `kotlin.IllegalArgumentException` leaking out of `Uuid.parse` as an unhandled 500. */
class IllegalIdFormatException(message: String = "Invalid ID format") :
    SquadifyException(message, "VAL_002")

class ConflictException(message: String) : 
    SquadifyException(message, "DATA_001")

class NotFoundException(message: String) : 
    SquadifyException(message, "DATA_003")

class DatabaseException(message: String) : 
    SquadifyException(message, "DATA_002")
