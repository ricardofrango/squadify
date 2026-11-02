package com.ricardofrango.squadify.coach.di

import com.ricardofrango.squadify.coach.data.AccountDeletionRepository
import com.ricardofrango.squadify.coach.data.CoachEmailService
import com.ricardofrango.squadify.coach.data.ExposedAccountDeletionRepository
import com.ricardofrango.squadify.coach.domain.DeleteAccountUseCase
import com.ricardofrango.squadify.coach.domain.GetAccountDeletionSummaryUseCase
import com.ricardofrango.squadify.coach.domain.StaffInviteMailer
import com.ricardofrango.squadify.data.repository.ExposedEmailChangeRepository
import com.ricardofrango.squadify.data.repository.ExposedPasswordResetRepository
import com.ricardofrango.squadify.domain.repository.EmailChangeRepository
import com.ricardofrango.squadify.domain.repository.PasswordResetRepository
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.usecase.ChangePasswordUseCase
import com.ricardofrango.squadify.domain.usecase.ConfirmEmailChangeUseCase
import com.ricardofrango.squadify.domain.usecase.GetCurrentUserUseCase
import com.ricardofrango.squadify.domain.usecase.RequestEmailChangeUseCase
import com.ricardofrango.squadify.domain.usecase.RequestPasswordResetUseCase
import com.ricardofrango.squadify.domain.usecase.ResetPasswordUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateProfileUseCase
import com.ricardofrango.squadify.coach.access.CoachAccessPolicy
import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.data.CoachDatabase
import com.ricardofrango.squadify.coach.data.ExposedStaffInviteRepository
import com.ricardofrango.squadify.coach.data.StaffInviteRepository
import com.ricardofrango.squadify.coach.access.CoachPushAudience
import com.ricardofrango.squadify.data.service.FirebaseAdminPushNotificationService
import com.ricardofrango.squadify.data.service.LocalImageStorage
import com.ricardofrango.squadify.data.service.S3ImageStorage
import com.ricardofrango.squadify.data.service.UnavailableImageStorage
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.PrefixedImageStorage
import com.ricardofrango.squadify.domain.service.PushAudience
import com.ricardofrango.squadify.domain.usecase.RegisterFcmTokenUseCase
import com.ricardofrango.squadify.domain.usecase.RemoveFcmTokenUseCase
import com.ricardofrango.squadify.coach.data.NoTrainingRatings
import com.ricardofrango.squadify.coach.data.NoTrainingSessions
import com.ricardofrango.squadify.coach.domain.AcceptStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.CoachSeasons
import com.ricardofrango.squadify.coach.domain.CreateStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.GetStaffInvitesUseCase
import com.ricardofrango.squadify.coach.domain.RemoveStaffUseCase
import com.ricardofrango.squadify.coach.domain.RevokeStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.data.repository.ExposedAgeGroupRepository
import com.ricardofrango.squadify.data.repository.ExposedClubRepository
import com.ricardofrango.squadify.data.repository.ExposedFcmTokenRepository
import com.ricardofrango.squadify.data.repository.ExposedMatchEventRepository
import com.ricardofrango.squadify.data.repository.ExposedMatchPlayerStatsRepository
import com.ricardofrango.squadify.data.repository.ExposedMatchReportRepository
import com.ricardofrango.squadify.data.repository.ExposedMatchRepository
import com.ricardofrango.squadify.data.repository.ExposedOpponentRepository
import com.ricardofrango.squadify.data.repository.ExposedPlayerRepository
import com.ricardofrango.squadify.data.repository.ExposedSeasonRepository
import com.ricardofrango.squadify.data.repository.ExposedTeamRepository
import com.ricardofrango.squadify.data.repository.ExposedUserRepository
import com.ricardofrango.squadify.data.service.KotlinFlowClubMatchesBus
import com.ricardofrango.squadify.data.service.KotlinFlowMatchBus
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import java.io.File
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
    single<MatchPlayerStatsRepository> { ExposedMatchPlayerStatsRepository() }
    single<MatchReportRepository> { ExposedMatchReportRepository() }
    // No training in the coach product: full stats finds none.
    single<TrainingSessionRepository> { NoTrainingSessions }
    single<TrainingRatingRepository> { NoTrainingRatings }

    // The live game's updates, to every device following a match or the space's matches.
    single<LiveMatchBus> { KotlinFlowMatchBus() }
    single<ClubMatchesBus> { KotlinFlowClubMatchesBus() }
    // Pushes, through the coach's own Firebase project (squadify-for-coach), to the space's owner and the
    // age group's staff. Its devices' tokens come through the shared fcmTokenRoutes.
    // Its picture is the space's logo, as on the clubs server.
    single<PushNotificationService> {
        val clubRepository = get<ClubRepository>()
        val transactionManager = get<TransactionManager>()
        FirebaseAdminPushNotificationService(CoachConfig.firebaseServiceAccountJson, clubLogoUrl = { spaceId ->
            val logoUrl = transactionManager.runInTransaction { clubRepository.getClubById(spaceId)?.logoUrl }
            GetPublicClubLogoUseCase.publicUrl(CoachConfig.publicBaseUrl, spaceId, logoUrl)
        })
    }
    factory<PushAudience> { CoachPushAudience(get(), get()) }
    factory { RegisterFcmTokenUseCase(get(), get()) }
    factory { RemoveFcmTokenUseCase(get(), get()) }
    // Images (TECHNICAL_DECISIONS §57, §72): its own bucket, keys under `coach/`, the disk locally,
    // none deployed without a bucket. The space's logo (a space is a club), opponents' logos, players'
    // photos and the account's own photo, through the shared use cases and imageRoutes.
    single<ImageStorage> {
        val bucket = CoachConfig.storageBucket
        PrefixedImageStorage(
            when {
                bucket != null -> S3ImageStorage(
                    bucket, CoachConfig.storageEndpoint, CoachConfig.storageRegion,
                    CoachConfig.storageAccessKeyId, CoachConfig.storageSecretAccessKey
                )
                !CoachConfig.deployed -> LocalImageStorage(File(CoachConfig.localImageDir))
                else -> UnavailableImageStorage
            },
            prefix = "coach/"
        )
    }
    factory { UploadClubLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteClubLogoUseCase(get(), get(), get(), get()) }
    factory { GetClubLogoUseCase(get(), get(), get(), get()) }
    factory { UploadOpponentLogoUseCase(get(), get(), get(), get(), get()) }
    factory { DeleteOpponentLogoUseCase(get(), get(), get(), get(), get()) }
    factory { GetOpponentLogoUseCase(get(), get(), get(), get()) }
    factory { UploadPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { DeletePlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { GetPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { UploadUserAvatarUseCase(get(), get(), get()) }
    factory { DeleteUserAvatarUseCase(get(), get(), get()) }
    factory { GetUserAvatarUseCase(get(), get(), get()) }
    // Pictures in pushes, served without signing in (publicImageRoutes): the space's and opponents'
    // current logos, and a scorer's photo behind a signed, expiring link.
    single { PublicImageLinks(CoachConfig.publicBaseUrl, CoachConfig.jwtSecret) }
    factory { GetPublicClubLogoUseCase(get(), get(), get()) }
    factory { GetPublicOpponentLogoUseCase(get(), get(), get()) }
    factory { GetPublicPlayerPhotoUseCase(get(), get(), get(), get()) }
    // No player or parent accounts: emails given with a player are refused.
    single<PlayerContacts> { PlayerContacts.None }

    // Accounts: the shared sign-up, sign-in and refresh. The space comes with the first setup.
    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get(), CoachTokens) }
    factory { RefreshTokenUseCase(get(), get(), CoachTokens) }

    // The account (accountRoutes, shared with the clubs server): profile, password, email change and
    // Forgot password, with the coach's own emails. The profile photo is in imageRoutes.
    single { CoachEmailService() }
    single<EmailService> { get<CoachEmailService>() }
    single<StaffInviteMailer> { get<CoachEmailService>() }
    single<PasswordResetRepository> { ExposedPasswordResetRepository() }
    single<EmailChangeRepository> { ExposedEmailChangeRepository() }
    factory { CurrentPasswordGuard(get(), get()) }
    factory { GetCurrentUserUseCase(get(), get()) }
    factory { UpdateProfileUseCase(get(), get()) }
    factory { ChangePasswordUseCase(get(), get(), get()) }
    factory { RequestPasswordResetUseCase(get(), get(), get(), get()) }
    factory { ResetPasswordUseCase(get(), get(), get()) }
    factory { RequestEmailChangeUseCase(get(), get(), get(), get(), get()) }
    factory { ConfirmEmailChangeUseCase(get(), get(), get(), get()) }
    // Deleting the account, with the space they own (the stores require it).
    single<AccountDeletionRepository> { ExposedAccountDeletionRepository() }
    factory { GetAccountDeletionSummaryUseCase(get(), get()) }
    factory { DeleteAccountUseCase(get(), get(), get(), get()) }

    // Who may do what: the space's owner everywhere, its staff in their age groups.
    factory<AccessPolicy> { CoachAccessPolicy(get(), get(), get(), get()) }
    factory { CoachSeasons(get()) }
    factory { SetUpSpaceUseCase(get(), get(), get(), get(), get()) }
    factory { GetSpacesUseCase(get(), get(), get()) }

    // Staff: invite codes for an age group, and taking someone off one.
    single<StaffInviteRepository> { ExposedStaffInviteRepository() }
    factory { CreateStaffInviteUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetStaffInvitesUseCase(get(), get(), get(), get()) }
    factory { RevokeStaffInviteUseCase(get(), get(), get(), get()) }
    factory { AcceptStaffInviteUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { RemoveStaffUseCase(get(), get(), get(), get()) }

    // Age groups, teams, players and opponents: the shared use cases.
    factory { CreateTeamUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTeamsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
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

    // Matches, call-ups, the live game, the record and report, and stats: the shared use cases.
    factory { GetAgeGroupSeasonRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetTeamSeasonRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetFullStatsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayerSeasonStatsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
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
    factory { NotifyMatchEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AddMatchEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SaveMatchRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { DeleteMatchEventUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetMatchEventsUseCase(get(), get(), get(), get()) }
    factory { GetMatchScoreUseCase(get(), get(), get(), get()) }
    factory { GetLiveMatchUpdatesUseCase(get(), get(), get(), get()) }
    factory { GetClubMatchesUpdatesUseCase(get(), get(), get()) }
}
