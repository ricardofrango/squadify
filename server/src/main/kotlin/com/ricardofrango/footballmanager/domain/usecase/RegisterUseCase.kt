package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ConflictException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.RegisterRequest
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RegisterUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(request: RegisterRequest): User {
        if (request.email.isBlank() || request.password.isBlank()) {
            throw ValidationException("Email and password are required")
        }

        if (userRepository.findByEmail(request.email) != null) {
            throw ConflictException("User with this email already exists")
        }

        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

        val user = User(
            id = Uuid.random().toString(),
            email = request.email,
            phone = request.phone,
            firstName = request.firstName,
            lastName = request.lastName,
            status = UserStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )

        return userRepository.createUser(user, passwordHash)
    }
}
