package com.sportssoftw.squadify.coach.di

import com.sportssoftw.squadify.domain.usecase.CreateExportGrantUseCase
import com.sportssoftw.squadify.domain.usecase.ExportAgeGroupUseCase
import com.sportssoftw.squadify.domain.usecase.ImportAgeGroupUseCase
import com.sportssoftw.squadify.domain.usecase.ListExportableAgeGroupsUseCase
import com.sportssoftw.squadify.domain.usecase.ListImportableAgeGroupsUseCase
import com.sportssoftw.squadify.data.repository.ExposedExportGrantRepository
import com.sportssoftw.squadify.data.repository.ExposedImportRepository
import com.sportssoftw.squadify.domain.repository.ExportGrantRepository
import com.sportssoftw.squadify.domain.repository.ImportRepository
import com.sportssoftw.squadify.coach.data.AccountDeletionRepository
import com.sportssoftw.squadify.coach.data.CoachEmailService
import com.sportssoftw.squadify.coach.data.ExposedAccountDeletionRepository
import com.sportssoftw.squadify.coach.data.ExposedSpacePlanRepository
import com.sportssoftw.squadify.coach.data.SpacePlanRepository
import com.sportssoftw.squadify.coach.domain.CoachPlans
import com.sportssoftw.squadify.coach.domain.GoogleSubscriptions
import com.sportssoftw.squadify.coach.domain.HandleGoogleNotificationUseCase
import com.sportssoftw.squadify.coach.domain.VerifyGooglePurchaseUseCase
import com.sportssoftw.squadify.coach.data.ExposedStoreSubscriptionRepository
import com.sportssoftw.squadify.coach.data.GooglePlayApi
import com.sportssoftw.squadify.coach.data.GooglePlayDeveloperApi
import com.sportssoftw.squadify.coach.data.StoreSubscriptionRepository
import com.sportssoftw.squadify.model.PlanChange
import com.sportssoftw.squadify.coach.domain.PlanRequestMailer
import com.sportssoftw.squadify.coach.domain.RequestPlanUseCase
import com.sportssoftw.squadify.coach.domain.DeleteAccountUseCase
import com.sportssoftw.squadify.coach.domain.DeleteSpaceUseCase
import com.sportssoftw.squadify.coach.domain.LeaveSpaceUseCase
import com.sportssoftw.squadify.coach.data.ExposedSpaceMembershipRepository
import com.sportssoftw.squadify.coach.data.SpaceMembershipRepository
import com.sportssoftw.squadify.coach.domain.GetAccountDeletionSummaryUseCase
import com.sportssoftw.squadify.coach.domain.GetAdminSpacesUseCase
import com.sportssoftw.squadify.coach.domain.PlatformAdmins
import com.sportssoftw.squadify.coach.domain.SetSpacePlanUseCase
import com.sportssoftw.squadify.coach.domain.StaffInviteMailer
import com.sportssoftw.squadify.data.repository.ExposedEmailChangeRepository
import com.sportssoftw.squadify.data.repository.ExposedPasswordResetRepository
import com.sportssoftw.squadify.domain.repository.EmailChangeRepository
import com.sportssoftw.squadify.domain.repository.PasswordResetRepository
import com.sportssoftw.squadify.domain.service.CurrentPasswordGuard
import com.sportssoftw.squadify.domain.service.EmailService
import com.sportssoftw.squadify.domain.usecase.ChangePasswordUseCase
import com.sportssoftw.squadify.domain.usecase.ConfirmEmailChangeUseCase
import com.sportssoftw.squadify.domain.usecase.GetCurrentUserUseCase
import com.sportssoftw.squadify.domain.usecase.RequestEmailChangeUseCase
import com.sportssoftw.squadify.domain.usecase.RequestPasswordResetUseCase
import com.sportssoftw.squadify.domain.usecase.ResetPasswordUseCase
import com.sportssoftw.squadify.domain.usecase.UpdateProfileUseCase
import com.sportssoftw.squadify.coach.access.CoachAccessPolicy
import com.sportssoftw.squadify.coach.config.CoachConfig
import com.sportssoftw.squadify.coach.data.CoachDatabase
import com.sportssoftw.squadify.coach.data.ExposedStaffInviteRepository
import com.sportssoftw.squadify.coach.data.StaffInviteRepository
import com.sportssoftw.squadify.coach.access.CoachPushAudience
import com.sportssoftw.squadify.data.service.FirebaseAdminPushNotificationService
import com.sportssoftw.squadify.data.service.LocalImageStorage
import com.sportssoftw.squadify.data.service.S3ImageStorage
import com.sportssoftw.squadify.data.service.UnavailableImageStorage
import com.sportssoftw.squadify.domain.service.ImageStorage
import com.sportssoftw.squadify.domain.service.PrefixedImageStorage
import com.sportssoftw.squadify.domain.service.PushAudience
import com.sportssoftw.squadify.domain.usecase.RegisterFcmTokenUseCase
import com.sportssoftw.squadify.domain.usecase.RemoveFcmTokenUseCase
import com.sportssoftw.squadify.coach.domain.AcceptStaffInviteUseCase
import com.sportssoftw.squadify.coach.domain.CoachSeasons
import com.sportssoftw.squadify.coach.domain.CreateStaffInviteUseCase
import com.sportssoftw.squadify.coach.domain.GetStaffInvitesUseCase
import com.sportssoftw.squadify.coach.domain.RemoveStaffUseCase
import com.sportssoftw.squadify.coach.domain.RevokeStaffInviteUseCase
import com.sportssoftw.squadify.coach.domain.GetPlansUseCase
import com.sportssoftw.squadify.coach.domain.AddAgeGroupsUseCase
import com.sportssoftw.squadify.coach.domain.GetSpaceDetailsUseCase
import com.sportssoftw.squadify.coach.domain.GetSpacesUseCase
import com.sportssoftw.squadify.coach.domain.SetUpSpaceUseCase
import com.sportssoftw.squadify.data.repository.ExposedAgeGroupRepository
import com.sportssoftw.squadify.data.repository.ExposedClubRepository
import com.sportssoftw.squadify.data.repository.ExposedFcmTokenRepository
import com.sportssoftw.squadify.data.repository.ExposedMatchEventRepository
import com.sportssoftw.squadify.data.repository.ExposedMatchPlayerStatsRepository
import com.sportssoftw.squadify.data.repository.ExposedMatchReportRepository
import com.sportssoftw.squadify.data.repository.ExposedMatchRepository
import com.sportssoftw.squadify.data.repository.ExposedOpponentRepository
import com.sportssoftw.squadify.data.repository.ExposedPlayerRepository
import com.sportssoftw.squadify.data.repository.ExposedSeasonRepository
import com.sportssoftw.squadify.data.repository.ExposedTeamRepository
import com.sportssoftw.squadify.data.repository.ExposedUserRepository
import com.sportssoftw.squadify.data.service.KotlinFlowClubMatchesBus
import com.sportssoftw.squadify.data.service.KotlinFlowMatchBus
import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.FcmTokenRepository
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchPlayerStatsRepository
import com.sportssoftw.squadify.domain.repository.MatchReportRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.TrainingRatingRepository
import com.sportssoftw.squadify.data.repository.ExposedTrainingPlanRepository
import com.sportssoftw.squadify.data.repository.ExposedTrainingSessionRepository
import com.sportssoftw.squadify.data.repository.ExposedTrainingExerciseRepository
import com.sportssoftw.squadify.data.repository.ExposedTrainingRatingRepository
import com.sportssoftw.squadify.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadify.domain.repository.TrainingExerciseRepository
import com.sportssoftw.squadify.domain.usecase.GetTrainingPlansUseCase
import com.sportssoftw.squadify.domain.usecase.SaveTrainingPlanUseCase
import com.sportssoftw.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.sportssoftw.squadify.domain.usecase.CreateTrainingSessionsUseCase
import com.sportssoftw.squadify.domain.usecase.SaveTrainingSessionUseCase
import com.sportssoftw.squadify.domain.usecase.ChangeTrainingSessionUseCase
import com.sportssoftw.squadify.domain.usecase.GetTrainingSessionUseCase
import com.sportssoftw.squadify.domain.usecase.TrainingRatingsUseCase
import com.sportssoftw.squadify.domain.usecase.TrainingStatsUseCase
import com.sportssoftw.squadify.domain.usecase.TrainingExercisesUseCase
import com.sportssoftw.squadify.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.ClubMatchesBus
import com.sportssoftw.squadify.domain.service.LiveMatchBus
import com.sportssoftw.squadify.domain.service.PushNotificationService
import com.sportssoftw.squadify.domain.service.TransactionManager
import java.io.File
import com.sportssoftw.squadify.domain.usecase.*
import com.sportssoftw.squadify.security.JwtTokens
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
    single<ExportGrantRepository> { ExposedExportGrantRepository() }
    single<ImportRepository> { ExposedImportRepository() }
    factory { CreateExportGrantUseCase(get(), get()) }
    factory { ListExportableAgeGroupsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ListImportableAgeGroupsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ExportAgeGroupUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), sourceServer = CoachConfig.publicBaseUrl.orEmpty()) }
    factory { ImportAgeGroupUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<MatchPlayerStatsRepository> { ExposedMatchPlayerStatsRepository() }
    single<MatchReportRepository> { ExposedMatchReportRepository() }
    // Training (Pro): the same repositories and use cases as the clubs server.
    single<TrainingPlanRepository> { ExposedTrainingPlanRepository() }
    single<TrainingSessionRepository> { ExposedTrainingSessionRepository() }
    single<TrainingExerciseRepository> { ExposedTrainingExerciseRepository() }
    single<TrainingRatingRepository> { ExposedTrainingRatingRepository() }
    factory { GetTrainingPlansUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SaveTrainingPlanUseCase(get(), get(), get(), get(), get()) }
    factory { GetTrainingSessionsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTrainingSessionsUseCase(get(), get(), get(), get(), get()) }
    factory { SaveTrainingSessionUseCase(get(), get(), get(), get()) }
    factory { ChangeTrainingSessionUseCase(get(), get(), get(), get()) }
    factory { GetTrainingSessionUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingRatingsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingStatsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingExercisesUseCase(get(), get(), get(), get(), get()) }

    // The live game's updates, to every device following a match or the space's matches.
    single<LiveMatchBus> { KotlinFlowMatchBus() }
    single<ClubMatchesBus> { KotlinFlowClubMatchesBus() }
    // Pushes, through the coach's own Firebase project (squadify-for-coach), to the space's owner and the
    // age group's staff. Its devices' tokens come through the shared fcmTokenRoutes.
    // Its picture is the space's logo, as on the clubs server.
    single<PushNotificationService> {
        val clubRepository = get<ClubRepository>()
        val transactionManager = get<TransactionManager>()
        val matchRepository = get<MatchRepository>()
        val opponentRepository = get<OpponentRepository>()
        val publicImageLinks = get<PublicImageLinks>()
        FirebaseAdminPushNotificationService(CoachConfig.firebaseServiceAccountJson, clubLogoUrl = { spaceId ->
            val logoUrl = transactionManager.runInTransaction { clubRepository.getClubById(spaceId)?.logoUrl }
            GetPublicClubLogoUseCase.publicUrl(CoachConfig.publicBaseUrl, spaceId, logoUrl)
        }, opponentLogoUrl = { matchId ->
            val opponent = transactionManager.runInTransaction {
                matchRepository.getMatchById(matchId)?.let { opponentRepository.getOpponentById(it.opponentId) }
            }
            opponent?.let { publicImageLinks.opponentLogo(it.id, it.logoUrl) }
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
    factory { UploadAgeGroupLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteAgeGroupLogoUseCase(get(), get(), get(), get()) }
    factory { GetAgeGroupLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteAgeGroupUseCase(get(), get(), get(), get(), get()) }
    factory { DeleteOpponentUseCase(get(), get(), get(), get(), get(), get()) }
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
    single<PlanRequestMailer> { get<CoachEmailService>() }
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
    factory { DeleteSpaceUseCase(get(), get(), get(), get(), get()) }
    single<SpaceMembershipRepository> { ExposedSpaceMembershipRepository() }
    factory { LeaveSpaceUseCase(get(), get(), get()) }

    // Who may do what: the space's owner everywhere, its staff in their age groups.
    // Plans (REQUIREMENTS §5c): what each space's plan allows and locks, and the admin who sets them.
    single<SpacePlanRepository> { ExposedSpacePlanRepository() }
    factory { CoachPlans(get(), plansEnabled = CoachConfig.plansEnabled) }
    factory { PlatformAdmins(get(), CoachConfig.platformAdminEmails) }
    factory { GetAdminSpacesUseCase(get(), get(), get()) }
    factory { SetSpacePlanUseCase(get(), get(), get(), get()) }
    factory<AccessPolicy> { CoachAccessPolicy(get(), get(), get(), get(), get()) }
    factory { CoachSeasons(get()) }
    factory { SetUpSpaceUseCase(get(), get(), get(), get(), get()) }
    factory { GetSpacesUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetSpaceDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AddAgeGroupsUseCase(get(), get(), get(), get(), get(), get()) }
    // How plans change: at once locally; deployed, in the store once it can check purchases, else by request.
    single<GooglePlayApi> { GooglePlayDeveloperApi(CoachConfig.googlePlayPackageName, CoachConfig.googlePlayServiceAccountJson) }
    single<StoreSubscriptionRepository> { ExposedStoreSubscriptionRepository() }
    factory {
        val changeBy = when {
            !CoachConfig.deployed -> PlanChange.INSTANT
            get<GooglePlayApi>().configured -> PlanChange.STORE
            else -> PlanChange.REQUEST
        }
        GetPlansUseCase(get(), get(), get(), changeBy, get())
    }
    factory { GoogleSubscriptions(get(), get(), get(), CoachConfig.acceptsTestPurchases, CoachConfig.acceptsRealPurchases) }
    factory { VerifyGooglePurchaseUseCase(get(), get(), get(), get(), get(), plansEnabled = CoachConfig.plansEnabled) }
    factory { HandleGoogleNotificationUseCase(get(), get(), get(), get(), get()) }
    // Locally a plan asked for is set at once; deployed, a platform admin sets it.
    factory { RequestPlanUseCase(get(), get(), get(), get(), CoachConfig.platformAdminEmails, atOnce = !CoachConfig.deployed, transactionManager = get(), plansEnabled = CoachConfig.plansEnabled) }

    // Staff: invite codes for an age group, and taking someone off one.
    single<StaffInviteRepository> { ExposedStaffInviteRepository() }
    factory { CreateStaffInviteUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetStaffInvitesUseCase(get(), get(), get(), get()) }
    factory { RevokeStaffInviteUseCase(get(), get(), get(), get()) }
    factory { AcceptStaffInviteUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
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
    factory { TakeOverLiveMatchUseCase(get(), get(), get(), get(), get()) }
    factory { ResumeMatchUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SetMatchMinuteUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ManageCallUpsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { NotifyMatchEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AddMatchEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SaveMatchRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { DeleteMatchEventUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetMatchEventsUseCase(get(), get(), get(), get()) }
    factory { GetMatchScoreUseCase(get(), get(), get(), get()) }
    factory { GetLiveMatchUpdatesUseCase(get(), get(), get(), get()) }
    factory { GetClubMatchesUpdatesUseCase(get(), get(), get()) }
}
