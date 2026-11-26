package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.RegisterRequest
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What a server does once an account exists, in the same transaction: the clubs server accepts the
 * invites waiting for that email (AcceptInvitesOnRegister). The coach server has none yet.
 */
fun interface NewAccountListener {
    suspend fun onRegistered(user: User)
}

/** Creates an account (shared by both servers, TECHNICAL_DECISIONS §72), then runs each server's [listeners]. */
class RegisterUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val listeners: List<NewAccountListener> = emptyList()
) {
    suspend operator fun invoke(request: RegisterRequest): User = transactionManager.runInTransaction {
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

        val createdUser = userRepository.createUser(user, passwordHash)
        listeners.forEach { it.onRegistered(createdUser) }
        createdUser
    }
}
