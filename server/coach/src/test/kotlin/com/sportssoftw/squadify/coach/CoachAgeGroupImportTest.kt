package com.sportssoftw.squadify.coach

import com.sportssoftw.squadify.coach.di.coachModule
import com.sportssoftw.squadify.coach.domain.PlanLimits
import com.sportssoftw.squadify.domain.usecase.ImportAgeGroupUseCase
import com.sportssoftw.squadify.model.AgeGroupBundle
import com.sportssoftw.squadify.model.AgeGroupItemRequest
import com.sportssoftw.squadify.model.CreateMatchRequest
import com.sportssoftw.squadify.model.CreateOpponentRequest
import com.sportssoftw.squadify.model.CreatePlayer
import com.sportssoftw.squadify.model.CreatePlayersRequest
import com.sportssoftw.squadify.model.ExportGrantResponse
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchType
import com.sportssoftw.squadify.model.MatchVenue
import com.sportssoftw.squadify.model.Opponent
import com.sportssoftw.squadify.model.Player
import com.sportssoftw.squadify.model.SetUpSpaceRequest
import com.sportssoftw.squadify.model.SpaceSetup
import com.sportssoftw.squadify.model.SportType
import com.sportssoftw.squadify.model.SaveExerciseRequest
import com.sportssoftw.squadify.model.SaveTrainingSessionRequest
import com.sportssoftw.squadify.model.SetSessionExercisesRequest
import com.sportssoftw.squadify.model.TrainingExercise
import com.sportssoftw.squadify.model.TrainingSession
import com.sportssoftw.squadify.model.TrainingSessions
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import io.ktor.client.call.body
import com.sportssoftw.squadify.exception.ConflictException
import com.sportssoftw.squadify.domain.usecase.ListImportableAgeGroupsUseCase
import com.sportssoftw.squadify.model.ImportBlock
import com.sportssoftw.squadify.model.ExportableAgeGroup
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication

// Copying an age group into another space (TECHNICAL_DECISIONS §81), on one server: Ana's age group is exported
// with a grant (single use), and Ben's space imports it. Ben gets the teams, players, opponent and match; the
// second import of the same age group is refused.
class CoachAgeGroupImportTest {

    private val png: ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", it)
    }.toByteArray()

    @Test
    fun anAgeGroupCopiedFromAnotherSpaceArrivesWithItsContents() = testApplication {
        application { module() }
        val api = jsonClient()

        // Ana's space: Sub-12, one player, an opponent and a scheduled match.
        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val created0 = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(setup.space.id, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 4, 2))), ageGroupId = setup.ageGroup.id, addToAgeGroup = true))
        }.body<List<Player>>()
        assertEquals(1, created0.size, "created: $created0")
        val rui = created0.single()
        // Rui's photo: it comes across as a file.
        assertEquals(HttpStatusCode.OK, api.put("/v1/players/${rui.id}/photo") { bearerAuth(ana); setBody(png) }.status)
        val opponent = api.post("/v1/opponents?clubId=${setup.space.id}") { json(ana, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        api.post("/v1/matches") {
            json(ana, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()
        // Training is on Pro: one exercise, and a session with it.
        api.setPlan(setup.space.id, PlanLimits.PRO)
        val exercise = api.post("/v1/age-groups/${setup.ageGroup.id}/exercises") { json(ana, SaveExerciseRequest("Rondo")) }.body<TrainingExercise>()
        val session = api.post("/v1/age-groups/${setup.ageGroup.id}/training-sessions") {
            json(ana, SaveTrainingSessionRequest(LocalDate(2026, 3, 10), LocalTime(18, 0), LocalTime(19, 30), location = "Campo 2"))
        }.body<TrainingSession>()
        api.put("/v1/training-sessions/${session.id}/exercises") { json(ana, SetSessionExercisesRequest(listOf(exercise.id))) }

        // The export: a grant lists the age group, its bundle comes once, and the grant is spent.
        val grantResponse = api.post("/v1/export/grants") { bearerAuth(ana) }
        assertEquals(HttpStatusCode.OK, grantResponse.status, grantResponse.bodyAsText())
        val grant = grantResponse.body<ExportGrantResponse>()
        val exportable = api.get("/v1/export/age-groups") { header("X-Export-Grant", grant.token) }.body<List<ExportableAgeGroup>>()
        assertEquals(setup.ageGroup.id, exportable.single().id)
        val bundle = api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.body<AgeGroupBundle>()
        assertEquals(1, bundle.players.size, "bundle: $bundle")
        assertEquals(1, bundle.matches.size)
        assertEquals(1, bundle.exercises.size)
        assertEquals(listOf(exercise.id), bundle.sessions.single().exerciseIds)
        assertEquals(HttpStatusCode.Unauthorized, api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.status)

        // The import into Ben's space (a Pro plan has room for the age group).
        val ben = api.signUp()
        val benSpace = api.post("/v1/space") {
            json(ben, SetUpSpaceRequest("Ben's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2016, 2016, teamSize = 4), "Sub-10 A"))
        }.body<SpaceSetup>().space.id
        api.setPlan(benSpace, PlanLimits.PRO)
        val benId = api.get("/v1/users/me") { bearerAuth(ben) }.body<com.sportssoftw.squadify.model.User>().id

        val koin = koinApplication { modules(coachModule) }.koin
        try {
            val importUseCase = koin.get<ImportAgeGroupUseCase>()
            val result = importUseCase(benId, benSpace, "https://ana.example", bundle)
            assertEquals(1, result.teams)
            assertEquals(1, result.players)
            val copiedPlayers = api.get("/v1/players?clubId=$benSpace") { bearerAuth(ben) }.body<List<Player>>()
            assertNotNull(copiedPlayers.single().photoUrl, "the copied player has no photo")
            assertEquals(1, result.opponents)
            assertEquals(1, result.matches)
            // The session comes across with its exercise.
            val copiedSessions = api.get("/v1/age-groups/${result.ageGroupId}/training-sessions") { bearerAuth(ben) }.body<TrainingSessions>().sessions
            assertEquals(listOf(LocalTime(18, 0)), copiedSessions.map { it.startTime })

            // The copy is in Ben's space, with its player and match.
            val ageGroups = api.get("/v1/age-groups?clubId=$benSpace") { bearerAuth(ben) }.body<List<com.sportssoftw.squadify.model.AgeGroup>>()
            assertEquals(setOf("Sub-10", "Sub-12"), ageGroups.map { it.name }.toSet())

            // Imported again, it's refused: there is still one copy.
            assertFailsWith<ConflictException> { importUseCase(benId, benSpace, "https://ana.example", bundle) }
            assertEquals(2, api.get("/v1/age-groups?clubId=$benSpace") { bearerAuth(ben) }.body<List<com.sportssoftw.squadify.model.AgeGroup>>().size)

            // The import list says why each offered age group can't be copied: imported, another sport, or a birth-year clash
            // with Ben's Sub-10 (2016) in the same season. Ben's club plays football; the season is Ana's (the same dates).
            val season = bundle.season
            fun offer(id: String, name: String, year: Int, sport: SportType) = ExportableAgeGroup(
                id, "ana-club", "Ana's team", name, year, year, season.name, sport, season.type, season.startDate, season.endDate
            )
            val listed = ListImportableAgeGroupsUseCase(
                koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get()
            )(benId, benSpace, "https://ana.example", listOf(
                offer(setup.ageGroup.id, "Sub-12", 2015, SportType.FOOTBALL),
                offer("futsal-1", "Sub-13", 2014, SportType.FUTSAL),
                offer("clash-1", "Sub-10", 2016, SportType.FOOTBALL),
                offer("free-1", "Sub-14", 2013, SportType.FOOTBALL)
            ))
            assertEquals(listOf(ImportBlock.ALREADY_IMPORTED, ImportBlock.OTHER_SPORT, ImportBlock.BIRTH_YEARS_TAKEN, null), listed.map { it.blocked })
            assertEquals("Sub-10", listed[2].clashName)
            assertEquals(2016, listed[2].clashStartYear)

            // An age group from another season (other dates) is never copied in: listed as such, and the import is refused
            // without making a season for it.
            val otherSeason = ExportableAgeGroup(
                "other-1", "ana-club", "Ana's team", "Sub-14", 2013, 2013, "2016/17", SportType.FOOTBALL, season.type,
                kotlinx.datetime.LocalDate(2016, 9, 1), kotlinx.datetime.LocalDate(2017, 6, 30)
            )
            val otherListed = ListImportableAgeGroupsUseCase(
                koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get()
            )(benId, benSpace, "https://ana.example", listOf(otherSeason))
            assertEquals(listOf(ImportBlock.OTHER_SEASON), otherListed.map { it.blocked })
        } finally {
            stopKoin()
        }
    }

    // Imported, deleted, and imported again: the first copy's records (team, player, match ids) are still there
    // after the delete, and the second import must not point at them.
    @Test
    fun anAgeGroupImportedDeletedAndImportedAgainIsCopiedAgain() = testApplication {
        application { module() }
        val api = jsonClient()

        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        api.post("/v1/players") {
            json(ana, CreatePlayersRequest(setup.space.id, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 4, 2))), ageGroupId = setup.ageGroup.id, addToAgeGroup = true))
        }
        val opponent = api.post("/v1/opponents?clubId=${setup.space.id}") { json(ana, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        api.post("/v1/matches") {
            json(ana, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()

        val ben = api.signUp()
        val benSpace = api.post("/v1/space") {
            json(ben, SetUpSpaceRequest("Ben's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2016, 2016, teamSize = 4), "Sub-10 A"))
        }.body<SpaceSetup>().space.id
        api.setPlan(benSpace, PlanLimits.PRO)
        val benId = api.get("/v1/users/me") { bearerAuth(ben) }.body<com.sportssoftw.squadify.model.User>().id

        val koin = koinApplication { modules(coachModule) }.koin
        try {
            val importUseCase = koin.get<ImportAgeGroupUseCase>()
            suspend fun exportBundle(): AgeGroupBundle {
                val grant = api.post("/v1/export/grants") { bearerAuth(ana) }.body<ExportGrantResponse>()
                return api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.body()
            }

            val first = importUseCase(benId, benSpace, "https://ana.example", exportBundle())
            assertEquals(1, first.matches)
            koin.get<com.sportssoftw.squadify.domain.usecase.DeleteAgeGroupUseCase>()(benId, first.ageGroupId)

            val second = importUseCase(benId, benSpace, "https://ana.example", exportBundle())
            assertEquals(1, second.teams)
            assertEquals(1, second.matches)
        } finally {
            stopKoin()
        }
    }
}
