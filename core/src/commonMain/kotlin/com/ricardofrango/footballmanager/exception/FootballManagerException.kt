package com.ricardofrango.footballmanager.exception

open class FootballManagerException(
    override val message: String,
    val code: String
) : Exception(message)

class UnauthorizedException(message: String = "Unauthorized") : 
    FootballManagerException(message, "AUTH_001")

class ForbiddenException(message: String = "Access Denied") : 
    FootballManagerException(message, "AUTH_002")

class ValidationException(message: String) : 
    FootballManagerException(message, "VAL_001")

class ConflictException(message: String) : 
    FootballManagerException(message, "DATA_001")

class NotFoundException(message: String) : 
    FootballManagerException(message, "DATA_003")

class DatabaseException(message: String) : 
    FootballManagerException(message, "DATA_002")
