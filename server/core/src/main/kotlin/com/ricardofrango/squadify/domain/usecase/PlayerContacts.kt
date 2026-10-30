package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerParentRequest

/**
 * What the emails given with a player become: the player's own and their parents'. Each server
 * supplies its own: the clubs server links or invites them as club members and parents
 * (`ClubPlayerContacts`); a server without player or parent accounts (the coach server) uses [None].
 * Both run inside the player use case's transaction.
 */
interface PlayerContacts {
    /** Once [players] exist, each with the request item it was created from. */
    suspend fun created(clubId: String, players: List<Pair<Player, CreatePlayer>>)

    /** Once [player] is saved. A null [email] or [parents] leaves them as they are; a list replaces the parents. */
    suspend fun updated(player: Player, email: String?, parents: List<PlayerParentRequest>?)

    /** How many parents' accounts are linked to the player, for their profile. */
    suspend fun guardiansLinked(playerId: String): Int

    /** No player or parent accounts: an email given anyway is refused, so it's never silently dropped. */
    object None : PlayerContacts {
        override suspend fun created(clubId: String, players: List<Pair<Player, CreatePlayer>>) =
            players.forEach { (_, item) -> refuse(item.email, item.parents) }

        override suspend fun updated(player: Player, email: String?, parents: List<PlayerParentRequest>?) =
            refuse(email, parents.orEmpty())

        override suspend fun guardiansLinked(playerId: String) = 0

        private fun refuse(email: String?, parents: List<PlayerParentRequest>) {
            if (email != null || parents.isNotEmpty()) {
                throw ValidationException("Players and parents don't have accounts here, so no emails can be added")
            }
        }
    }
}
