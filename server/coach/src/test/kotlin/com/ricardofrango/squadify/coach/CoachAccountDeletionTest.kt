package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.AccountDeletionSummary
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.DeleteAccountRequest
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SaveMatchReportRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.UpdateMatchStatusRequest
import com.ricardofrango.squadify.model.UpdateProfileRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

// Deleting a coach's account (REQUIREMENTS §5c): the warning's summary, the password asked for, a staff
// member's reports staying in the other space under its owner, and an owner's space going with everything
// in it while its staff lose access.
class CoachAccountDeletionTest {

    @Test
    fun deletingStaffThenTheOwner() = testApplication {
        application { module() }
        val api = jsonClient()

        // Ana's space: Sub-12, four players, a finished game.
        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        val ids = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(spaceId, (1..4).map { CreatePlayer("Player", "$it", LocalDate(2015, 1, it)) }, ageGroupId = setup.ageGroup.id))
        }.body<List<Player>>().map { it.id }
        api.post("/v1/age-groups/${setup.ageGroup.id}/players") { json(ana, AddPlayersToAgeGroupRequest(ids)) }
        val opponent = api.post("/v1/opponents?clubId=$spaceId") { json(ana, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        val match = api.post("/v1/matches") {
            json(ana, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()
        api.post("/v1/matches/${match.id}/call-up") { json(ana, ManageCallUpRequest(ids)) }
        api.post("/v1/matches/${match.id}/start") { json(ana, StartMatchRequest(ids, halfLengthMinutes = 20)) }
        api.patch("/v1/matches/${match.id}/status") { json(ana, UpdateMatchStatusRequest(MatchStatus.FINISHED)) }

        // Bruno owns a space of his own, joins Ana's Sub-12 and writes the game's report.
        val bruno = api.signUp()
        api.patch("/v1/users/me") { json(bruno, UpdateProfileRequest("Bruno", "Staff")) }
        api.post("/v1/space") { json(bruno, SetUpSpaceRequest("Bruno's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2017, 2017), "Sub-10 A")) }
        val code = api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(ana) }.body<StaffInvite>().code
        api.post("/v1/invites/$code/accept") { bearerAuth(bruno) }
        assertEquals(
            HttpStatusCode.OK,
            api.put("/v1/matches/${match.id}/report") { json(bruno, SaveMatchReportRequest(tactic = "4-0")) }.status
        )
        suspend fun reportAuthor() = api.get("/v1/matches/${match.id}/report") { bearerAuth(ana) }.body<MatchReportDetails>().updatedByName
        assertEquals("Bruno Staff", reportAuthor())

        // Bruno's warning names his own space; a wrong password deletes nothing.
        assertEquals("Bruno's team", api.get("/v1/users/me/deletion") { bearerAuth(bruno) }.body<AccountDeletionSummary>().ownSpaceName)
        assertEquals(HttpStatusCode.BadRequest, api.post("/v1/users/me/delete") { json(bruno, DeleteAccountRequest("wrong-one")) }.status)
        assertEquals(HttpStatusCode.NoContent, api.post("/v1/users/me/delete") { json(bruno, DeleteAccountRequest("password1")) }.status)

        // The report stays, under Ana; Bruno is off her staff.
        assertEquals("Ana Coach", reportAuthor())
        assertTrue(api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(ana) }.body<com.ricardofrango.squadify.model.AgeGroupDetails>().staff.isEmpty())

        // Carla joins Ana's Sub-12; Ana's warning counts what goes, then her space goes with her.
        val carla = api.signUp()
        val carlasCode = api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(ana) }.body<StaffInvite>().code
        api.post("/v1/invites/$carlasCode/accept") { bearerAuth(carla) }
        assertEquals(
            AccountDeletionSummary("Ana's team", ageGroups = 1, players = 4, matches = 1, staff = 1),
            api.get("/v1/users/me/deletion") { bearerAuth(ana) }.body<AccountDeletionSummary>()
        )
        assertEquals(HttpStatusCode.NoContent, api.post("/v1/users/me/delete") { json(ana, DeleteAccountRequest("password1")) }.status)
        assertTrue(api.get("/v1/spaces") { bearerAuth(carla) }.body<List<SpaceAgeGroups>>().isEmpty())
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/matches/${match.id}") { bearerAuth(carla) }.status)

        // Carla owns nothing: her warning has no space.
        assertNull(api.get("/v1/users/me/deletion") { bearerAuth(carla) }.body<AccountDeletionSummary>().ownSpaceName)
    }
}
