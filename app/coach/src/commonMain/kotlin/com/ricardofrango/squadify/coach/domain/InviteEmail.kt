package com.ricardofrango.squadify.coach.domain

/**
 * The address an invite code is emailed to, as typed on Invite staff. Checked here so a typo is caught
 * before the server is asked; the server checks it again.
 */
object InviteEmail {
    private val ADDRESS = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValid(typed: String): Boolean = ADDRESS.matches(typed.trim())
}
