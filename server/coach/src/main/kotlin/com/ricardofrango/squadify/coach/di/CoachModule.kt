package com.ricardofrango.squadify.coach.di

import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.data.CoachDatabase
import com.ricardofrango.squadify.data.repository.ExposedUserRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.usecase.LoginUseCase
import com.ricardofrango.squadify.domain.usecase.RefreshTokenUseCase
import com.ricardofrango.squadify.domain.usecase.RegisterUseCase
import com.ricardofrango.squadify.security.JwtTokens
import org.koin.dsl.module

/** The coach server's tokens: its own secret and audience (TECHNICAL_DECISIONS §72). */
object CoachTokens : JwtTokens(CoachConfig.jwtSecret, audience = "squadify-coach-users")

val coachModule = module {
    single<TransactionManager> { CoachDatabase }
    single<UserRepository> { ExposedUserRepository() }

    // Accounts: the shared sign-up, sign-in and refresh. Signing up does nothing more yet; the space
    // comes with the first setup.
    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get(), CoachTokens) }
    factory { RefreshTokenUseCase(get(), get(), CoachTokens) }
}
