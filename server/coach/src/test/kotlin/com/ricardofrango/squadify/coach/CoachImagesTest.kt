package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.DeleteAccountRequest
import com.ricardofrango.squadify.model.LogoResponse
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.PhotoResponse
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Images on the coach server (TECHNICAL_DECISIONS §57, §72): the space's logo is the owner's, players'
// photos and opponents' logos are their age group's staff's too, the account's photo is its own; each
// is served to the space's members (and the logo in pushes without signing in), stored under `coach/`,
// and deleted with the account.
class CoachImagesTest {

    // The tests' LocalImageStorage folder (build.gradle.kts).
    private val imageDir = File(System.getenv("LOCAL_IMAGE_DIR"))

    private fun stored(url: String) = File(imageDir, "coach$url")

    private val png: ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", it)
    }.toByteArray()

    private suspend fun HttpClient.upload(path: String, token: String): HttpResponse = put(path) {
        bearerAuth(token)
        setBody(png)
    }

    @Test
    fun ownerStaffAndOutsidersWithImages() = testApplication {
        application { module() }
        val api = jsonClient()

        // Ana's space: Sub-12 with Rui, Sub-14 with Rita. João joins Sub-12 with a code.
        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        // More than one age group, staff or stats: on Pro.
        api.setPlan(spaceId, PlanLimits.PRO)
        val sub14 = api.post("/v1/age-groups") {
            json(ana, CreateAgeGroupsRequest(spaceId, items = listOf(AgeGroupItemRequest("Sub-14", 2013, 2013))))
        }.body<List<AgeGroup>>().single()
        val rui = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 4, 2))), ageGroupId = setup.ageGroup.id))
        }.body<List<Player>>().single()
        val rita = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rita", "Lopes", LocalDate(2013, 1, 9))), ageGroupId = sub14.id))
        }.body<List<Player>>().single()
        api.post("/v1/age-groups/${setup.ageGroup.id}/players") { json(ana, AddPlayersToAgeGroupRequest(listOf(rui.id))) }
        api.post("/v1/age-groups/${sub14.id}/players") { json(ana, AddPlayersToAgeGroupRequest(listOf(rita.id))) }
        val opponent = api.post("/v1/opponents?clubId=$spaceId") { json(ana, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        val joao = api.signUp()
        val invite = api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(ana) }.body<StaffInvite>()
        assertEquals(HttpStatusCode.OK, api.post("/v1/invites/${invite.code}/accept") { bearerAuth(joao) }.status)
        val outsider = api.signUp()

        // The space's logo: only the owner changes it; members see it, in /spaces too; pushes get it without signing in.
        assertEquals(HttpStatusCode.Forbidden, api.upload("/v1/clubs/$spaceId/logo", joao).status)
        val logo = api.upload("/v1/clubs/$spaceId/logo", ana).body<LogoResponse>().logoUrl
        assertTrue(logo.startsWith("/clubs/$spaceId/logo/"))
        assertTrue(stored(logo).isFile)
        assertEquals(logo, api.get("/v1/spaces") { bearerAuth(joao) }.body<List<SpaceAgeGroups>>().single().space.logoUrl)
        assertEquals(HttpStatusCode.OK, api.get("/v1$logo") { bearerAuth(joao) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1$logo") { bearerAuth(outsider) }.status)
        assertEquals(HttpStatusCode.OK, api.get("/v1/public$logo").status)

        // Players' photos: João for Sub-12's players, not Sub-14's; outsiders can't see them.
        val ruiPhoto = api.upload("/v1/players/${rui.id}/photo", joao).body<PhotoResponse>().photoUrl
        assertTrue(stored(ruiPhoto).isFile)
        assertEquals(HttpStatusCode.Forbidden, api.upload("/v1/players/${rita.id}/photo", joao).status)
        val ritaPhoto = api.upload("/v1/players/${rita.id}/photo", ana).body<PhotoResponse>().photoUrl
        assertEquals(HttpStatusCode.OK, api.get("/v1$ruiPhoto") { bearerAuth(joao) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1$ruiPhoto") { bearerAuth(outsider) }.status)

        // An opponent's logo, from João's age group.
        val opponentLogo = api.upload("/v1/opponents/${opponent.id}/logo?ageGroupId=${setup.ageGroup.id}", joao).body<LogoResponse>().logoUrl
        assertTrue(stored(opponentLogo).isFile)

        // An age group's crest: its own staff change it (João for Sub-12, not Sub-14); the space sees it, in
        // the age group itself; outsiders don't.
        assertEquals(HttpStatusCode.Forbidden, api.upload("/v1/age-groups/${sub14.id}/logo", joao).status)
        val crest = api.upload("/v1/age-groups/${setup.ageGroup.id}/logo", joao).body<LogoResponse>().logoUrl
        assertTrue(crest.startsWith("/age-groups/${setup.ageGroup.id}/logo/"))
        assertTrue(stored(crest).isFile)
        assertEquals(crest, api.get("/v1/age-groups?clubId=$spaceId") { bearerAuth(ana) }.body<List<AgeGroup>>().single { it.id == setup.ageGroup.id }.logoUrl)
        assertEquals(HttpStatusCode.OK, api.get("/v1$crest") { bearerAuth(joao) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1$crest") { bearerAuth(outsider) }.status)
        val sub14Crest = api.upload("/v1/age-groups/${sub14.id}/logo", ana).body<LogoResponse>().logoUrl
        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/age-groups/${sub14.id}/logo") { bearerAuth(ana) }.status)
        assertFalse(stored(sub14Crest).exists())

        // The account's own photo, seen by anyone signed in.
        val anaPhoto = api.upload("/v1/users/me/avatar", ana).body<PhotoResponse>().photoUrl
        assertEquals(HttpStatusCode.OK, api.get("/v1$anaPhoto") { bearerAuth(joao) }.status)

        // Ana deletes her account: her space's images and her photo go with it.
        assertEquals(HttpStatusCode.NoContent, api.post("/v1/users/me/delete") { json(ana, DeleteAccountRequest("password1")) }.status)
        listOf(logo, ruiPhoto, ritaPhoto, opponentLogo, crest, anaPhoto).forEach { assertFalse(stored(it).exists(), it) }
    }
}
