package com.ricardofrango.squadify.di

import com.ricardofrango.squadify.security.JwtConfig
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.data.database.DatabaseFactory
import com.ricardofrango.squadify.data.repository.*
import com.ricardofrango.squadify.data.service.FirebaseAdminPushNotificationService
import com.ricardofrango.squadify.data.service.KotlinFlowClubMatchesBus
import com.ricardofrango.squadify.data.service.KotlinFlowMatchBus
import com.ricardofrango.squadify.data.service.ResendEmailService
import com.ricardofrango.squadify.domain.repository.*
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.data.service.LocalImageStorage
import com.ricardofrango.squadify.data.service.S3ImageStorage
import com.ricardofrango.squadify.data.service.UnavailableImageStorage
import com.ricardofrango.squadify.config.AppEnvironment
import java.io.File
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.ClubPushAudience
import com.ricardofrango.squadify.domain.service.PushAudience
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.usecase.*
import org.koin.dsl.module

val appModule = module {
    single<ClubRepository> { ExposedClubRepository() }
    single<UserRepository> { ExposedUserRepository() }
    single<PasswordResetRepository> { ExposedPasswordResetRepository() }
    single<EmailChangeRepository> { ExposedEmailChangeRepository() }
    single<TeamRepository> { ExposedTeamRepository() }
    single<SeasonRepository> { ExposedSeasonRepository() }
    single<AgeGroupRepository> { ExposedAgeGroupRepository() }
    single<PlayerRepository> { ExposedPlayerRepository() }
    single<InviteRepository> { ExposedInviteRepository() }
    single<MatchRepository> { ExposedMatchRepository() }
    single<MatchEventRepository> { ExposedMatchEventRepository() }
    single<MatchPlayerStatsRepository> { ExposedMatchPlayerStatsRepository() }
    single<MatchReportRepository> { ExposedMatchReportRepository() }
    single<OpponentRepository> { ExposedOpponentRepository() }
    single<TrainingPlanRepository> { ExposedTrainingPlanRepository() }
    single<TrainingSessionRepository> { ExposedTrainingSessionRepository() }
    single<TrainingExerciseRepository> { ExposedTrainingExerciseRepository() }
    single<TrainingRatingRepository> { ExposedTrainingRatingRepository() }
    single<FcmTokenRepository> { ExposedFcmTokenRepository() }
    single<ClubPermissionRepository> { ExposedClubPermissionRepository() }

    // Services
    single<LiveMatchBus> { KotlinFlowMatchBus() }
    single<ClubMatchesBus> { KotlinFlowClubMatchesBus() }
    single<TransactionManager> { DatabaseFactory }
    single<EmailService> { ResendEmailService() }
    // Club logos: a Railway Storage Bucket when one is configured, the disk in dev, none in production
    // without a bucket (a container's disk doesn't survive a deploy). See TECHNICAL_DECISIONS.md §57.
    single<ImageStorage> {
        val bucket = ServerConfig.storageBucket
        when {
            bucket != null -> S3ImageStorage(
                bucket, ServerConfig.storageEndpoint, ServerConfig.storageRegion,
                ServerConfig.storageAccessKeyId, ServerConfig.storageSecretAccessKey
            )
            ServerConfig.environment == AppEnvironment.DEV -> LocalImageStorage(File(ServerConfig.localImageDir))
            else -> UnavailableImageStorage
        }
    }
    // Who gets each push: the whole club for games and teams, the age group's staff for a ready call-up.
    factory<PushAudience> { ClubPushAudience(get(), get()) }
    single<PushNotificationService> {
        val clubRepository = get<ClubRepository>()
        val transactionManager = get<TransactionManager>()
        FirebaseAdminPushNotificationService(ServerConfig.firebaseServiceAccountJson, clubLogoUrl = { clubId ->
            val logoUrl = transactionManager.runInTransaction { clubRepository.getClubById(clubId)?.logoUrl }
            GetPublicClubLogoUseCase.publicUrl(ServerConfig.publicBaseUrl, clubId, logoUrl)
        })
    }

    // Use Cases
    factory { GetClubsUseCase(get(), get(), get()) }
    factory { GetClubDetailsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetClubHomeUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ProvisionClubUseCase(get(), get(), get(), get(), get(), get()) }
    // Signing up accepts the invites waiting for that email.
    factory { RegisterUseCase(get(), get(), listOf(AcceptInvitesOnRegister(get(), get(), get()))) }
    factory {
        SeedPlatformAdminUseCase(
            get(),
            get(),
            enabled = ServerConfig.seedAdminEnabled,
            adminEmail = ServerConfig.seedAdminEmail,
            adminPassword = ServerConfig.seedAdminPassword,
        )
    }
    factory { LoginUseCase(get(), get(), JwtConfig) }
    factory { GetCurrentUserUseCase(get(), get()) }
    factory { UpdateProfileUseCase(get(), get()) }
    factory { CurrentPasswordGuard(get(), get()) }
    factory { ChangePasswordUseCase(get(), get(), get()) }
    factory { RequestPasswordResetUseCase(get(), get(), get(), get()) }
    factory { ResetPasswordUseCase(get(), get(), get()) }
    factory { RequestEmailChangeUseCase(get(), get(), get(), get(), get()) }
    factory { ConfirmEmailChangeUseCase(get(), get(), get(), get()) }
    factory { RefreshTokenUseCase(get(), get(), JwtConfig) }
    factory { CheckUserPermissionUseCase(get(), get(), get(), get(), get(), get()) }
    // Who may do what with games (TECHNICAL_DECISIONS §72): the clubs server's roles and permissions.
    factory<AccessPolicy> { ClubAccessPolicy(get()) }
    factory { GetClubPermissionsUseCase(get(), get(), get()) }
    factory { UpdateClubPermissionsUseCase(get(), get(), get()) }
    factory { CheckUserIsAdminUseCase(get(), get()) }
    factory { AddClubMembersUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetClubMembersUseCase(get(), get(), get()) }
    factory { UpdateClubMemberRoleUseCase(get(), get(), get(), get()) }
    factory { RemoveClubMemberUseCase(get(), get(), get(), get(), get()) }
    factory { GetClubMembersOverviewUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ManageClubInviteUseCase(get(), get(), get(), get(), get()) }
    factory { UpdateClubUseCase(get(), get(), get()) }
    factory { UploadClubLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteClubLogoUseCase(get(), get(), get(), get()) }
    factory { GetClubLogoUseCase(get(), get(), get(), get()) }
    factory { GetPublicClubLogoUseCase(get(), get(), get()) }
    single { PublicImageLinks(ServerConfig.publicBaseUrl, ServerConfig.jwtSecret) }
    factory { GetPublicOpponentLogoUseCase(get(), get(), get()) }
    factory { GetPublicPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { UploadOpponentLogoUseCase(get(), get(), get(), get(), get()) }
    factory { DeleteOpponentLogoUseCase(get(), get(), get(), get(), get()) }
    factory { GetOpponentLogoUseCase(get(), get(), get(), get()) }
    factory { UploadPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { DeletePlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { GetPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { UploadUserAvatarUseCase(get(), get(), get()) }
    factory { DeleteUserAvatarUseCase(get(), get(), get()) }
    factory { GetUserAvatarUseCase(get(), get(), get()) }

    factory { CreateTeamUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTeamsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AddStaffToAgeGroupUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { RemoveStaffFromAgeGroupUseCase(get(), get(), get()) }
    factory { GetAgeGroupStaffUseCase(get(), get(), get()) }
    factory { GetTeamPlayersUseCase(get(), get(), get(), get()) }
    factory { GetTeamDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetAgeGroupSeasonRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetTeamSeasonRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateTeamUseCase(get(), get(), get(), get()) }
    factory { DeleteTeamUseCase(get(), get(), get(), get(), get()) }

    factory { GetSeasonsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { CreateSeasonUseCase(get(), get(), get()) }
    factory { UpdateSeasonUseCase(get(), get(), get()) }

    factory { GetAgeGroupsUseCase(get(), get(), get()) }
    factory { CreateAgeGroupsUseCase(get(), get(), get(), get()) }
    factory { UpdateAgeGroupUseCase(get(), get(), get(), get(), get()) }
    factory { GetTrainingPlansUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SaveTrainingPlanUseCase(get(), get(), get(), get(), get()) }
    factory { GetTrainingSessionsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTrainingSessionsUseCase(get(), get(), get(), get(), get()) }
    factory { SaveTrainingSessionUseCase(get(), get(), get(), get()) }
    factory { ChangeTrainingSessionUseCase(get(), get(), get(), get()) }
    factory { GetTrainingSessionUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingRatingsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingStatsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetFullStatsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingExercisesUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    factory { GetPlayerUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayerSeasonStatsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayersUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupPlayersByClubUseCase(get(), get(), get()) }
    factory<PlayerContacts> { ClubPlayerContacts(get(), get(), get(), get()) }
    factory { CreatePlayersUseCase(get(), get(), get(), get(), get()) }
    factory { UpdatePlayerUseCase(get(), get(), get(), get(), get()) }
    factory { AddPlayersToAgeGroupUseCase(get(), get(), get(), get()) }
    factory { RemovePlayerFromAgeGroupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { DisablePlayerUseCase(get(), get(), get(), get(), get()) }
    factory { ReactivatePlayerUseCase(get(), get(), get()) }
    factory { GetEligiblePlayersForAgeGroupUseCase(get(), get(), get(), get()) }

    factory { GetTeamsUseCase(get(), get(), get()) }
    factory { CreateMatchUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetMatchesUseCase(get(), get(), get(), get(), get()) }
    factory { GetMatchDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateMatchUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { DeleteMatchUseCase(get(), get(), get(), get()) }
    factory { GetMatchReportUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SaveMatchReportUseCase(get(), get(), get(), get(), get()) }
    factory { UpdateMatchStatusUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { StartMatchUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SetMatchLineupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { PauseMatchUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ResumeMatchUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SetMatchMinuteUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ManageCallUpsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateOpponentUseCase(get(), get(), get(), get()) }
    factory { GetOpponentsUseCase(get(), get(), get(), get()) }
    factory { GetOpponentListUseCase(get(), get(), get(), get(), get()) }
    factory { GetOpponentDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateOpponentUseCase(get(), get(), get(), get()) }
    factory { RegisterFcmTokenUseCase(get(), get()) }
    factory { RemoveFcmTokenUseCase(get(), get()) }
    factory { NotifyMatchEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    factory { AddMatchEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SaveMatchRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { DeleteMatchEventUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetMatchEventsUseCase(get(), get(), get(), get()) }
    factory { GetMatchScoreUseCase(get(), get(), get(), get()) }
    factory { GetLiveMatchUpdatesUseCase(get(), get(), get(), get()) }
    factory { GetClubMatchesUpdatesUseCase(get(), get(), get()) }

    // Dashboards
    factory { GetSuperAdminDashboardUseCase(get(), get(), get(), get()) }
    factory { GetClubAdminDashboardUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetParentDashboardUseCase(get(), get(), get(), get()) }
}
