package com.ricardofrango.squadify.coach.domain

/**
 * An invite code as typed (TECHNICAL_DECISIONS §72): six letters and digits, shown as "K7Q-4MZ", taken in
 * any case, with or without the dash or spaces.
 */
object JoinCode {
    const val LENGTH = 6

    /** Upper case, letters and digits only, as the server stores it. */
    fun normalize(typed: String): String = typed.uppercase().filter { it.isLetterOrDigit() }

    /** Enough typed to try it. */
    fun isComplete(typed: String): Boolean = normalize(typed).length == LENGTH

    /** Why a code was refused, from the server's answer. */
    enum class Refusal { NOT_FOUND, EXPIRED, USED_OR_ALREADY_ON, NO_ROOM_ON_PLAN, OTHER }

    /** [notInPlan]: the age group has no room for one more person on its space's plan. */
    fun refusal(statusCode: Int?, notInPlan: Boolean = false): Refusal = if (notInPlan) Refusal.NO_ROOM_ON_PLAN else when (statusCode) {
        404 -> Refusal.NOT_FOUND
        400 -> Refusal.EXPIRED
        // Used already, or the coach owns the space or is already staff on the age group.
        409 -> Refusal.USED_OR_ALREADY_ON
        else -> Refusal.OTHER
    }
}
