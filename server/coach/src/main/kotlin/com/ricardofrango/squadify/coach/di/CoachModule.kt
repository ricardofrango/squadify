package com.ricardofrango.squadify.coach.di

import com.ricardofrango.squadify.coach.access.CoachAccessPolicy
import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.data.CoachDatabase
import com.ricardofrango.squadify.coach.data.NoPushNotifications
import com.ricardofrango.squadify.coach.domain.CoachSeasons
import com.ricardofrango.squadify.coach.domain.GetSpaceUseCase
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.data.repository.ExposedAgeGroupRepository
import com.ricardofrango.squadify.data.repository.ExposedClubRepository
import com.ricardofrango.squadify.data.repository.ExposedFcmTokenRepository
import com.ricardofrango.squadify.data.repository.ExposedMatchEventRepository
import com.ricardofrango.squadify.data.repository.ExposedMatchRepository
import com.ricardofrango.squadify.data.repository.ExposedOpponentRepository
import com.ricardofrango.squadify.data.repository.ExposedPlayerRepository
import com.ricardofrango.squadify.data.repository.ExposedSeasonRepository
import com.ricardofrango.squadify.data.repository.ExposedTeamRepository
import com.ricardofrango.squadify.data.repository.ExposedUserRepository
import com.ricardofrango.squadify.data.service.KotlinFlowClubMatchesBus
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.usecase.*
import com.ricardofrango.squadify.security.JwtTokens
import org.koin.dsl.module

/** The coach server's tokens: its own secret and audience (TECHNICAL_DECISIONS §72). */
object CoachTokens : JwtTokens(CoachConfig.jwtSecret, audience = "squadify-coach-users")

val coachModule = module {
    single<TransactionManager> { CoachDatabase }

    // The shared repositories, on the coach's database. Only the shared tables exist there, so the
    // clubs-only parts (parents' links, invites) stay behind the clubs server's own code.
    single<UserRepository> { ExposedUserRepository() }
    single<ClubRepository> { ExposedClubRepository() }
    single<SeasonRepository> { ExposedSeasonRepository() }
    single<AgeGroupRepository> { ExposedAgeGroupRepository() }
    single<TeamRepository> { ExposedTeamRepository() }
    single<PlayerRepository> { ExposedPlayerRepository() }
    single<OpponentRepository> { ExposedOpponentRepository() }
    single<MatchRepository> { ExposedMatchRepository() }
    single<MatchEventRepository> { ExposedMatchEventRepository() }
    single<FcmTokenRepository> { ExposedFcmTokenRepository() }

    single<ClubMatchesBus> { KotlinFlowClubMatchesBus() }
    single<PushNotificationService> { NoPushNotifications }
    // No player or parent accounts: emails given with a player are refused.
    single<PlayerContacts> { PlayerContacts.None }

    // Accounts: the shared sign-up, sign-in and refresh. The space comes with the first setup.
    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get(), CoachTokens) }
    factory { RefreshTokenUseCase(get(), get(), CoachTokens) }

    // Who may do what: the space's owner everywhere, its staff in their age groups.
    factory<AccessPolicy> { CoachAccessPolicy(get(), get(), get(), get()) }
    factory { CoachSeasons(get()) }
    factory { SetUpSpaceUseCase(get(), get(), get(), get(), get()) }
    factory { GetSpaceUseCase(get(), get()) }

    // Age groups, teams, players and opponents: the shared use cases.
    factory { CreateTeamUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTeamsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetTeamPlayersUseCase(get(), get(), get(), get()) }
    factory { GetTeamDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateTeamUseCase(get(), get(), get(), get()) }
    factory { DeleteTeamUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupsUseCase(get(), get(), get()) }
    factory { CreateAgeGroupsUseCase(get(), get(), get(), get()) }
    factory { UpdateAgeGroupUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayerUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayersUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupPlayersByClubUseCase(get(), get(), get()) }
    factory { CreatePlayersUseCase(get(), get(), get(), get(), get()) }
    factory { UpdatePlayerUseCase(get(), get(), get(), get(), get()) }
    factory { AddPlayersToAgeGroupUseCase(get(), get(), get(), get()) }
    factory { RemovePlayerFromAgeGroupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetEligiblePlayersForAgeGroupUseCase(get(), get(), get(), get()) }
    factory { GetTeamsUseCase(get(), get(), get()) }
    factory { CreateOpponentUseCase(get(), get(), get(), get()) }
    factory { GetOpponentsUseCase(get(), get(), get(), get()) }
    factory { GetOpponentListUseCase(get(), get(), get(), get(), get()) }
    factory { GetOpponentDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateOpponentUseCase(get(), get(), get(), get()) }
}
