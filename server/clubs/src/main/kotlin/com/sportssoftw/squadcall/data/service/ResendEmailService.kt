package com.sportssoftw.squadcall.data.service

import com.sportssoftw.squadcall.config.ServerConfig
import com.sportssoftw.squadcall.domain.service.EmailService
import com.sportssoftw.squadcall.email.AccountEmails
import com.sportssoftw.squadcall.email.Email
import com.sportssoftw.squadcall.email.EmailLayout
import com.sportssoftw.squadcall.email.EmailLayout.button
import com.sportssoftw.squadcall.email.EmailLayout.greeting
import com.sportssoftw.squadcall.email.EmailLayout.note
import com.sportssoftw.squadcall.email.EmailLayout.paragraph
import com.sportssoftw.squadcall.email.ResendSender
import com.sportssoftw.squadcall.email.escapeHtml
import com.sportssoftw.squadcall.email.isPortuguese

/**
 * Sends via Resend's API (https://resend.com/docs/api-reference/emails/send-email), in the shared look
 * ([EmailLayout]) and in the language of the app that asked (English or Portuguese, the clubs app's
 * formal "você"): the user's own for the account emails ([AccountEmails], shared with the coach server),
 * the admin's for the club ones. Until a real sending domain is verified, Resend only allows
 * [ServerConfig.emailFromAddress]'s default (onboarding@resend.dev) to deliver to the Resend account's
 * own verified email -- see TECHNICAL_DECISIONS.md.
 */
class ResendEmailService : EmailService {
    // The sending itself is shared with the coach server (:server:web).
    private val sender = ResendSender(ServerConfig.resendApiKey, ServerConfig.emailFromAddress)
    private val account = AccountEmails(PRODUCT, informal = false, askTheClub = true)

    override suspend fun sendClubInvite(to: String, clubName: String, language: String?) {
        val club = clubName.escapeHtml()
        val address = to.escapeHtml()
        send(to, if (isPortuguese(language)) clubEmail(
            subject = "Recebeu um convite para o $clubName no Squadcall",
            club = club,
            language = language,
            content = greeting("Olá,") +
                paragraph("Recebeu um convite para fazer parte do <strong>$club</strong> no Squadcall.") +
                paragraph("Crie a sua conta com este endereço de email ($address) e passa logo a fazer parte do clube:") +
                button("Abrir o Squadcall", ServerConfig.webAppBaseUrl) +
                note("Se não estava à espera deste convite, pode ignorar este email.")
        ) else clubEmail(
            subject = "You've been invited to join $clubName on Squadcall",
            club = club,
            language = language,
            content = greeting("Hello,") +
                paragraph("You've been invited to join <strong>$club</strong> on Squadcall.") +
                paragraph("Create your account with this email address ($address) and you'll join the club straight away:") +
                button("Open Squadcall", ServerConfig.webAppBaseUrl) +
                note("If you weren't expecting this invite, you can ignore this email.")
        ))
    }

    override suspend fun sendClubMembershipAdded(to: String, firstName: String, clubName: String, language: String?) {
        val club = clubName.escapeHtml()
        val name = firstName.escapeHtml()
        send(to, if (isPortuguese(language)) clubEmail(
            subject = "Já faz parte do $clubName no Squadcall",
            club = club,
            language = language,
            content = greeting("Olá $name,") +
                paragraph("Passou a fazer parte do <strong>$club</strong> no Squadcall.") +
                button("Abrir o Squadcall", ServerConfig.webAppBaseUrl)
        ) else clubEmail(
            subject = "You've been added to $clubName on Squadcall",
            club = club,
            language = language,
            content = greeting("Hi $name,") +
                paragraph("You've been added to <strong>$club</strong> on Squadcall.") +
                button("Open Squadcall", ServerConfig.webAppBaseUrl)
        ))
    }

    override suspend fun sendAgeGroupStaffAdded(to: String, firstName: String, ageGroupName: String, clubName: String, language: String?) {
        val club = clubName.escapeHtml()
        val ageGroup = ageGroupName.escapeHtml()
        val name = firstName.escapeHtml()
        send(to, if (isPortuguese(language)) clubEmail(
            subject = "Já faz parte da equipa técnica do $ageGroupName no Squadcall",
            club = club,
            language = language,
            content = greeting("Olá $name,") +
                paragraph("Passou a fazer parte da equipa técnica do <strong>$ageGroup</strong> do <strong>$club</strong> no Squadcall.") +
                button("Abrir o Squadcall", ServerConfig.webAppBaseUrl)
        ) else clubEmail(
            subject = "You've been added to $ageGroupName on Squadcall",
            club = club,
            language = language,
            content = greeting("Hi $name,") +
                paragraph("You've been added as staff to <strong>$ageGroup</strong> at <strong>$club</strong> on Squadcall.") +
                button("Open Squadcall", ServerConfig.webAppBaseUrl)
        ))
    }

    override suspend fun sendPasswordResetCode(to: String, firstName: String, code: String, validMinutes: Int, language: String?) {
        send(to, account.passwordResetCode(firstName, code, validMinutes, language))
    }

    override suspend fun sendEmailChangeCode(to: String, firstName: String, code: String, validMinutes: Int, language: String?) {
        send(to, account.emailChangeCode(firstName, code, validMinutes, language))
    }

    override suspend fun sendEmailChanged(to: String, firstName: String, newEmail: String, language: String?) {
        send(to, account.emailChanged(firstName, newEmail, language))
    }

    /** A club email: [club] is already escaped. */
    private fun clubEmail(subject: String, club: String, language: String?, content: String): Email {
        val footer = if (isPortuguese(language)) "Recebe este email por causa do $club no Squadcall."
        else "You're receiving this email because of $club on Squadcall."
        return Email(subject, EmailLayout.page(PRODUCT, language, content, footer))
    }

    private suspend fun send(to: String, email: Email) {
        sender.send(to, email.subject, email.html)
    }

    private companion object {
        const val PRODUCT = "Squadcall"
    }
}
