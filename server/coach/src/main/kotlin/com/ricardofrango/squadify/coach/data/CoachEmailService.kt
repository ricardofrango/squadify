package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.email.ResendSender
import org.slf4j.LoggerFactory

/**
 * The coach server's emails (TECHNICAL_DECISIONS §72): the account's codes and notices, as "Squadify
 * Coach", through the shared [ResendSender]. In English for now, like the clubs server's; the user's
 * language and a designed template with the app's logo come before the launch (REQUIREMENTS, launch
 * readiness).
 *
 * The coach product has no clubs and adds staff with codes, so the club and age-group emails are never
 * asked for here; they only log.
 */
class CoachEmailService(
    private val sender: ResendSender = ResendSender(CoachConfig.resendApiKey, CoachConfig.emailFromAddress)
) : EmailService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun sendClubInvite(to: String, clubName: String) = notInTheCoachProduct("club invite")

    override suspend fun sendClubMembershipAdded(to: String, firstName: String, clubName: String) = notInTheCoachProduct("club membership")

    override suspend fun sendAgeGroupStaffAdded(to: String, firstName: String, ageGroupName: String, clubName: String) =
        notInTheCoachProduct("age group staff")

    override suspend fun sendPasswordResetCode(to: String, firstName: String, code: String, validMinutes: Int) {
        sender.send(
            to = to,
            subject = "Your Squadify Coach password reset code: $code",
            html = """
                <p>Hi $firstName,</p>
                <p>Use this code in Squadify Coach to set a new password:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">$code</p>
                <p>It expires in $validMinutes minutes. If you didn't ask to reset your password, you can ignore this email -- your password hasn't changed.</p>
            """.trimIndent()
        )
    }

    override suspend fun sendEmailChangeCode(to: String, firstName: String, code: String, validMinutes: Int) {
        sender.send(
            to = to,
            subject = "Your Squadify Coach email change code: $code",
            html = """
                <p>Hi $firstName,</p>
                <p>Use this code in Squadify Coach to confirm this address as your new sign-in email:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">$code</p>
                <p>It expires in $validMinutes minutes. If you didn't ask for this, you can ignore this email.</p>
            """.trimIndent()
        )
    }

    override suspend fun sendEmailChanged(to: String, firstName: String, newEmail: String) {
        sender.send(
            to = to,
            subject = "Your Squadify Coach email was changed",
            html = """
                <p>Hi $firstName,</p>
                <p>The email you sign in to Squadify Coach with was changed to <strong>$newEmail</strong>. From now on, sign in with that address.</p>
                <p>If you didn't make this change, reply to this email straight away.</p>
            """.trimIndent()
        )
    }

    private fun notInTheCoachProduct(what: String) {
        logger.warn("A {} email was asked for on the coach server, which has none; not sent", what)
    }
}
