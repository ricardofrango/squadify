package com.ricardofrango.clubmanager.exception

open class ClubManagerException(
    override val message: String,
    val code: String
) : Exception(message)

class UnauthorizedException(message: String = "Unauthorized") : 
    ClubManagerException(message, "AUTH_001")

class ForbiddenException(message: String = "Access Denied") : 
    ClubManagerException(message, "AUTH_002")

class ValidationException(message: String) : 
    ClubManagerException(message, "VAL_001")

class ConflictException(message: String) : 
    ClubManagerException(message, "DATA_001")

class NotFoundException(message: String) : 
    ClubManagerException(message, "DATA_003")

class DatabaseException(message: String) : 
    ClubManagerException(message, "DATA_002")
