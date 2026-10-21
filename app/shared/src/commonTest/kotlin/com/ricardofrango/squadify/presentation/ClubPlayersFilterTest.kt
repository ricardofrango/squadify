package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.domain.usecase.ClubDetails
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.clubdetails.filterClubPlayers
import com.ricardofrango.squadify.presentation.ui.clubdetails.withPlayerDisabled
import com.ricardofrango.squadify.presentation.ui.clubdetails.withPlayerReactivated
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

// The Players screen's filters: season and age group go through the age-group pools.
class ClubPlayersFilterTest {
    private fun player(id: String, first: String) = Player(id = id, clubId = "c", firstName = first, lastName = "X", birthDate = LocalDate(2012, 1, 1), active = true)

    private val details = ClubDetails(
        club = Club(id = "c", name = "CD", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL),
        teams = emptyList(),
        players = listOf(player("p1", "Tomás"), player("p2", "Rui"), player("p3", "Novo")),
        seasons = emptyList(),
        ageGroups = listOf(AgeGroup("a25", "c", "Sub-15", "s25", 2012, 2012), AgeGroup("a26", "c", "Sub-15", "s26", 2011, 2011)),
        ageGroupPlayers = listOf(AgeGroupPlayer("x1", "a26", "p1"), AgeGroupPlayer("x2", "a25", "p2")),
        canEditClub = true
    )

    private fun ids(seasonId: String?, ageGroupId: String?, query: String = "") = filterClubPlayers(details, seasonId, ageGroupId, query).map { it.id }

    @Test
    fun aSeasonShowsItsPoolsPlusPlayersInNoPoolYet() {
        assertEquals(listOf("p1", "p3"), ids("s26", null))
    }

    @Test
    fun anAgeGroupShowsOnlyItsPool() {
        assertEquals(listOf("p2"), ids(null, "a25"))
    }

    @Test
    fun searchMatchesTheName() {
        assertEquals(listOf("p1"), ids(null, null, "tom"))
    }

    @Test
    fun aDisabledPlayerStaysListedLastAndKeepsTheirPool() {
        val after = details.withPlayerDisabled("p1")
        assertFalse(after.players.first { it.id == "p1" }.active)
        // Still in their age group, and listed last.
        assertEquals(listOf("p1"), filterClubPlayers(after, null, "a26", "").map { it.id })
        assertEquals(listOf("p2", "p3", "p1"), filterClubPlayers(after, null, null, "").map { it.id })
    }

    @Test
    fun aReactivatedPlayerIsActiveAgainInTheSamePool() {
        val after = details.withPlayerDisabled("p1").withPlayerReactivated("p1")
        assertTrue(after.players.first { it.id == "p1" }.active)
        // Back among the active players, in the club's order, still in their age group.
        assertEquals(listOf("p1", "p2", "p3"), filterClubPlayers(after, null, null, "").map { it.id })
        assertEquals(listOf("p1"), filterClubPlayers(after, null, "a26", "").map { it.id })
    }
}
