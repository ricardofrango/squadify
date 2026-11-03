package com.ricardofrango.squadify.exception

import com.ricardofrango.squadify.model.PLAN_LIMIT_CODE

open class SquadifyException(
    override val message: String,
    val code: String
) : Exception(message)

class UnauthorizedException(message: String = "Unauthorized") : 
    SquadifyException(message, "AUTH_001")

class ForbiddenException(message: String = "Access Denied") : 
    SquadifyException(message, "AUTH_002")

/** The current password asked for by Change password / Change email is wrong. A 400, not a 401: the app
 * treats any 401 as an expired session. See CurrentPasswordGuard. */
class WrongPasswordException(message: String = "Current password is incorrect") :
    SquadifyException(message, "AUTH_003")

/** The third wrong current password in a row: the user has been signed out everywhere (403). */
class TooManyPasswordAttemptsException(message: String = "Too many wrong passwords. You have been signed out.") :
    SquadifyException(message, "AUTH_004")

class ValidationException(message: String) :
    SquadifyException(message, "VAL_001")

/** Not on the space's plan (the coach server, REQUIREMENTS §5c): stats on Free, an age group or a member
 * over the plan's limit. A 403 the app tells apart by its code, to show what the plan allows rather than
 * an error. The clubs server never throws it. */
class PlanLimitException(message: String) :
    SquadifyException(message, PLAN_LIMIT_CODE)

/** A path/body value that should be a UUID (an entity id) isn't one -- e.g. a stale client holding a
 * deleted resource's id, a typo'd URL, or a malformed request. Distinct from [ValidationException] so
 * callers of `String.toUuid()` (see `server/.../util/UuidParsing.kt`) get a specific, known exception
 * instead of a raw `kotlin.IllegalArgumentException` leaking out of `Uuid.parse` as an unhandled 500. */
class IllegalIdFormatException(message: String = "Invalid ID format") :
    SquadifyException(message, "VAL_002")

/** [rows]: for a batch request, the items (0-based, in request order) that conflict. */
class ConflictException(message: String, val rows: List<Int>? = null) :
    SquadifyException(message, "DATA_001")

class NotFoundException(message: String) : 
    SquadifyException(message, "DATA_003")

class DatabaseException(message: String) : 
    SquadifyException(message, "DATA_002")
