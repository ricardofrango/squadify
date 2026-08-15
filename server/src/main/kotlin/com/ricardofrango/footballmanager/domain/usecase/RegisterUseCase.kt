package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.RegisterRequest
import com.ricardofrango.footballmanager.model.User
import kotlinx.datetime.Clock
import org.mindrot.jbcrypt.BCrypt
import kotlin.uuid.Uuid
import kotlin.time.Instant

class RegisterUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(request: RegisterRequest): Result<Boolean> {
        if (request.email.isBlank() || request.password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password are required"))
        }

        if (userRepository.findByEmail(request.email) != null) {
            return Result.failure(IllegalArgumentException("User with this email already exists"))
        }

        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())
        
        val user = User(
            id = Uuid.random().toString(),
            email = request.email,
            phone = request.phone,
            firstName = request.firstName,
            lastName = request.lastName,
            status = "active",
            createdAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        )

        return try {
            val success = userRepository.createUser(user, passwordHash)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
