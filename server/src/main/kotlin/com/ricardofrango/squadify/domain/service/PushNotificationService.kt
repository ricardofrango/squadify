package com.ricardofrango.squadify.domain.service

/**
 * Best-effort push notifications (Firebase Cloud Messaging) for association events -- the push
 * equivalent of [EmailService]. Implementations must never throw: a failed send must never fail the
 * use case that triggered it, since the underlying database association already succeeded by the
 * time these are called. See TECHNICAL_DECISIONS.md.
 */
interface PushNotificationService {
    /**
     * Sent to every device in [tokens] when an existing user is added to a club directly (no
     * registration needed) -- the push equivalent of [EmailService.sendClubMembershipAdded]. Callers
     * are responsible for only invoking this with a non-empty [tokens] list (i.e. only for users who
     * actually have at least one registered device).
     */
    suspend fun sendClubMembershipAdded(tokens: List<String>, clubName: String)

    /**
     * Sent to every device in [tokens] when an existing user is added as staff/coach to an age group
     * -- the push equivalent of [EmailService.sendAgeGroupStaffAdded]. Same non-empty-[tokens]
     * contract as [sendClubMembershipAdded].
     */
    suspend fun sendAgeGroupStaffAdded(tokens: List<String>, ageGroupName: String, clubName: String)
}
