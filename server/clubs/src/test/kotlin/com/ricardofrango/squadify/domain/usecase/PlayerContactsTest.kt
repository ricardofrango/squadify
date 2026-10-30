package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerParentRequest
import com.ricardofrango.squadify.model.UserPlayerRelationship
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith

// Without player or parent accounts (the coach server), an email given with a player is refused,
// never silently dropped.
class PlayerContactsTest {
    private val birthDate = LocalDate(2014, 3, 1)
    private val player = Player(id = "p1", clubId = "c1", firstName = "Ana", lastName = "Silva", birthDate = birthDate, active = true)
    private val parent = PlayerParentRequest("mum@example.com", UserPlayerRelationship.MOTHER)

    @Test
    fun playersWithoutEmailsAreFine() = runBlocking {
        PlayerContacts.None.created("c1", listOf(player to CreatePlayer("Ana", "Silva", birthDate)))
        PlayerContacts.None.updated(player, email = null, parents = null)
        PlayerContacts.None.updated(player, email = null, parents = emptyList())
    }

    @Test
    fun aPlayersEmailIsRefused() {
        assertFailsWith<ValidationException> {
            runBlocking { PlayerContacts.None.created("c1", listOf(player to CreatePlayer("Ana", "Silva", birthDate, email = "ana@example.com"))) }
        }
        assertFailsWith<ValidationException> {
            runBlocking { PlayerContacts.None.updated(player, email = "ana@example.com", parents = null) }
        }
    }

    @Test
    fun aParentsEmailIsRefused() {
        assertFailsWith<ValidationException> {
            runBlocking { PlayerContacts.None.created("c1", listOf(player to CreatePlayer("Ana", "Silva", birthDate, parents = listOf(parent)))) }
        }
        assertFailsWith<ValidationException> {
            runBlocking { PlayerContacts.None.updated(player, email = null, parents = listOf(parent)) }
        }
    }
}
