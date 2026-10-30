package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.email.ResendSender

/**
 * Sends via Resend's API (https://resend.com/docs/api-reference/emails/send-email). Until a real
 * sending domain is verified, Resend only allows [ServerConfig.emailFromAddress]'s default
 * (onboarding@resend.dev) to deliver to the Resend account's own verified email -- see
 * TECHNICAL_DECISIONS.md.
 */
class ResendEmailService : EmailService {
    // The sending itself is shared with the coach server (:server:web).
    private val sender = ResendSender(ServerConfig.resendApiKey, ServerConfig.emailFromAddress)

    override suspend fun sendClubInvite(to: String, clubName: String) {
        send(
            to = to,
            subject = "You've been invited to join $clubName on Squadify",
            html = """
                <p>You've been invited to join <strong>$clubName</strong> on Squadify.</p>
                <p>Create your account using this email address ($to) to join automatically:</p>
                <p><a href="${ServerConfig.webAppBaseUrl}">${ServerConfig.webAppBaseUrl}</a></p>
            """.trimIndent()
        )
    }

    override suspend fun sendClubMembershipAdded(to: String, firstName: String, clubName: String) {
        send(
            to = to,
            subject = "You've been added to $clubName on Squadify",
            html = """
                <p>Hi $firstName,</p>
                <p>You've been added to <strong>$clubName</strong> on Squadify.</p>
                <p><a href="${ServerConfig.webAppBaseUrl}">${ServerConfig.webAppBaseUrl}</a></p>
            """.trimIndent()
        )
    }

    override suspend fun sendAgeGroupStaffAdded(to: String, firstName: String, ageGroupName: String, clubName: String) {
        send(
            to = to,
            subject = "You've been added to $ageGroupName on Squadify",
            html = """
                <p>Hi $firstName,</p>
                <p>You've been added as staff to <strong>$ageGroupName</strong> at <strong>$clubName</strong> on Squadify.</p>
                <p><a href="${ServerConfig.webAppBaseUrl}">${ServerConfig.webAppBaseUrl}</a></p>
            """.trimIndent()
        )
    }

    override suspend fun sendPasswordResetCode(to: String, firstName: String, code: String, validMinutes: Int) {
        send(
            to = to,
            subject = "Your Squadify password reset code: $code",
            html = """
                <p>Hi $firstName,</p>
                <p>Use this code to set a new password for your Squadify account:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">$code</p>
                <p>It expires in $validMinutes minutes. If you didn't ask to reset your password, you can ignore this email -- your password hasn't changed.</p>
            """.trimIndent()
        )
    }

    override suspend fun sendEmailChangeCode(to: String, firstName: String, code: String, validMinutes: Int) {
        send(
            to = to,
            subject = "Your Squadify email change code: $code",
            html = """
                <p>Hi $firstName,</p>
                <p>Use this code in Squadify to confirm this address as your new sign-in email:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">$code</p>
                <p>It expires in $validMinutes minutes. If you didn't ask for this, you can ignore this email.</p>
            """.trimIndent()
        )
    }

    override suspend fun sendEmailChanged(to: String, firstName: String, newEmail: String) {
        send(
            to = to,
            subject = "Your Squadify email was changed",
            html = """
                <p>Hi $firstName,</p>
                <p>The email you sign in to Squadify with was changed to <strong>$newEmail</strong>. From now on, sign in with that address.</p>
                <p>If you didn't make this change, reply to this email or contact your club straight away.</p>
            """.trimIndent()
        )
    }

    private suspend fun send(to: String, subject: String, html: String) = sender.send(to, subject, html)
}
