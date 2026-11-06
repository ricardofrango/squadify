package com.ricardofrango.squadify.domain.service

/**
 * Best-effort transactional email for association notifications (club membership, age-group staff).
 * Implementations must never throw -- a failed send should never fail the use case that triggered it,
 * since the underlying database association already succeeded by the time these are called. See
 * TECHNICAL_DECISIONS.md.
 *
 * [language] is the app's language of whoever's request sent it (its Accept-Language header): the
 * user's own for the account emails, the admin's for the club ones. English when it's missing.
 */
interface EmailService {
    /** Sent when someone without an account yet is added to a club -- asks them to register. */
    suspend fun sendClubInvite(to: String, clubName: String, language: String? = null)

    /** Sent when an existing user is added to a club directly (no registration needed). */
    suspend fun sendClubMembershipAdded(to: String, firstName: String, clubName: String, language: String? = null)

    /** Sent when an existing user is added as staff/coach to an age group. */
    suspend fun sendAgeGroupStaffAdded(to: String, firstName: String, ageGroupName: String, clubName: String, language: String? = null)

    /** Forgot password: the 6-digit code to set a new password, valid for [validMinutes]. */
    suspend fun sendPasswordResetCode(to: String, firstName: String, code: String, validMinutes: Int, language: String? = null)

    /** Change email: the 6-digit code, sent to the NEW address to prove it's the user's. */
    suspend fun sendEmailChangeCode(to: String, firstName: String, code: String, validMinutes: Int, language: String? = null)

    /** Change email, done: a heads-up to the OLD address, in case the change wasn't theirs. */
    suspend fun sendEmailChanged(to: String, firstName: String, newEmail: String, language: String? = null)
}
