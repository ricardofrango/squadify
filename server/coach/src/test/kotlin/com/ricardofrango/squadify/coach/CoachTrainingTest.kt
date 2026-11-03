package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.AgeGroupTrainingStats
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.DeleteAccountRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SaveExerciseRequest
import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.SaveTrainingRatingsRequest
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.SetSessionExercisesRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.TrainingRatingChange
import com.ricardofrango.squadify.model.TrainingRatings
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessions
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

// Training on the coach server (REQUIREMENTS §5c: on Pro), the clubs server's use cases behind the coach's
// access policy: refused below Pro; on Pro the owner and the age group's staff plan, schedule, build
// sessions from the library, rate and see the stats; and deleting an account deals with the training rows.
class CoachTrainingTest {

    private suspend fun HttpResponse.isPlanLimit() =
        status == HttpStatusCode.Forbidden && body<ErrorResponse>().code == "PLAN_001"

    @Test
    fun trainingIsOnProForTheOwnerAndTheStaff() = testApplication {
        application { module() }
        val api = jsonClient()
        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        val ageGroupId = setup.ageGroup.id
        val yesterday = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 1))
        val session = SaveTrainingSessionRequest(yesterday, LocalTime(18, 0), LocalTime(19, 30))

        // Below Pro: refused, reading and writing.
        api.setPlan(spaceId, PlanLimits.BASIC)
        assertTrue(api.get("/v1/age-groups/$ageGroupId/training-sessions") { bearerAuth(ana) }.isPlanLimit())
        assertTrue(api.post("/v1/age-groups/$ageGroupId/training-sessions") { json(ana, session) }.isPlanLimit())
        assertTrue(api.get("/v1/age-groups/$ageGroupId/training-plans") { bearerAuth(ana) }.isPlanLimit())

        // Pro: Bruno joins as staff; a player in the pool.
        api.setPlan(spaceId, PlanLimits.PRO)
        val bruno = api.signUp()
        val code = api.post("/v1/age-groups/$ageGroupId/invites") { bearerAuth(ana) }.body<StaffInvite>().code
        api.post("/v1/invites/$code/accept") { bearerAuth(bruno) }
        val player = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 1, 1))), ageGroupId = ageGroupId))
        }.body<List<Player>>().single()
        api.post("/v1/age-groups/$ageGroupId/players") { json(ana, AddPlayersToAgeGroupRequest(listOf(player.id))) }

        // The owner schedules a session and builds it from the library.
        val created = api.post("/v1/age-groups/$ageGroupId/training-sessions") { json(ana, session) }
        assertEquals(HttpStatusCode.Created, created.status)
        val sessionId = created.body<TrainingSession>().id
        val exercise = api.post("/v1/age-groups/$ageGroupId/exercises") { json(ana, SaveExerciseRequest("Rondo", durationMinutes = 15)) }.body<TrainingExercise>()
        assertEquals(HttpStatusCode.OK, api.put("/v1/training-sessions/$sessionId/exercises") { json(ana, SetSessionExercisesRequest(listOf(exercise.id))) }.status)

        // Staff see it, may edit and rate, and plan the season.
        val sessions = api.get("/v1/age-groups/$ageGroupId/training-sessions") { bearerAuth(bruno) }.body<TrainingSessions>()
        assertEquals(listOf(1), sessions.sessions.map { it.exerciseCount })
        assertTrue(sessions.canEdit && sessions.canRate)
        val rated = api.put("/v1/training-sessions/$sessionId/ratings") {
            json(bruno, SaveTrainingRatingsRequest(listOf(TrainingRatingChange(player.id, 7.5))))
        }.body<TrainingRatings>()
        assertEquals(listOf(7.5), rated.ratings.map { it.rating })
        val stats = api.get("/v1/age-groups/$ageGroupId/training-stats") { bearerAuth(ana) }.body<AgeGroupTrainingStats>()
        assertEquals(listOf(sessionId), stats.sessions.map { it.id })
        assertEquals(
            HttpStatusCode.OK,
            api.put("/v1/age-groups/$ageGroupId/training-plans") {
                json(bruno, SaveTrainingPlanRequest(TrainingPlanLevel.SEASON, setup.season.startDate, objectives = listOf("Press high")))
            }.status
        )

        // Someone outside the space sees nothing.
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/age-groups/$ageGroupId/training-sessions") { bearerAuth(api.signUp()) }.status)

        // Bruno leaves: his rating and plan stay, under Ana.
        assertEquals(HttpStatusCode.NoContent, api.post("/v1/users/me/delete") { json(bruno, DeleteAccountRequest("password1")) }.status)
        assertEquals(listOf(7.5), api.get("/v1/training-sessions/$sessionId/ratings") { bearerAuth(ana) }.body<TrainingRatings>().ratings.map { it.rating })
        assertEquals(1, api.get("/v1/age-groups/$ageGroupId/training-plans") { bearerAuth(ana) }.body<TrainingPlans>().plans.size)

        // Ana's account goes with her space and its training.
        assertEquals(HttpStatusCode.NoContent, api.post("/v1/users/me/delete") { json(ana, DeleteAccountRequest("password1")) }.status)
    }
}
