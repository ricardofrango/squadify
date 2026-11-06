package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.domain.PlanRequestEmail
import com.ricardofrango.squadify.coach.domain.PlanRequestMailer
import com.ricardofrango.squadify.coach.domain.StaffInviteEmail
import com.ricardofrango.squadify.coach.domain.StaffInviteMailer
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.email.AccountEmails
import com.ricardofrango.squadify.email.Email
import com.ricardofrango.squadify.email.EmailLayout
import com.ricardofrango.squadify.email.EmailLayout.code
import com.ricardofrango.squadify.email.EmailLayout.greeting
import com.ricardofrango.squadify.email.EmailLayout.note
import com.ricardofrango.squadify.email.EmailLayout.paragraph
import com.ricardofrango.squadify.email.ResendSender
import com.ricardofrango.squadify.email.escapeHtml
import com.ricardofrango.squadify.email.isPortuguese
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * The coach server's emails (TECHNICAL_DECISIONS §72): the account's codes and notices, as "Squadify
 * Coach", through the shared [ResendSender], in the shared look ([EmailLayout]) and in the language of
 * the app that asked (its Accept-Language, English or Portuguese, the coach app's "tu"); the account
 * emails' texts are shared with the clubs server ([AccountEmails]).
 *
 * The coach product has no clubs and adds staff with codes, so the club and age-group emails are never
 * asked for here; they only log. A code can be emailed ([sendStaffInvite]), in the owner's app language.
 * A plan request goes to the platform admins ([sendPlanRequest]), in English.
 *
 * On a local server with no Resend key ([sendsLocally]), an invite isn't sent but counts as sent, with
 * its code in the log, so invites can be tried on this machine; deployed, a missing key still fails it.
 */
class CoachEmailService(
    private val sender: ResendSender = ResendSender(CoachConfig.resendApiKey, CoachConfig.emailFromAddress),
    private val sendsLocally: Boolean = !CoachConfig.deployed && CoachConfig.resendApiKey.isBlank()
) : EmailService, StaffInviteMailer, PlanRequestMailer {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val account = AccountEmails(PRODUCT, informal = true, askTheClub = false)

    override suspend fun sendClubInvite(to: String, clubName: String, language: String?) = notInTheCoachProduct("club invite")

    override suspend fun sendClubMembershipAdded(to: String, firstName: String, clubName: String, language: String?) =
        notInTheCoachProduct("club membership")

    override suspend fun sendAgeGroupStaffAdded(to: String, firstName: String, ageGroupName: String, clubName: String, language: String?) =
        notInTheCoachProduct("age group staff")

    override suspend fun sendPasswordResetCode(to: String, firstName: String, code: String, validMinutes: Int, language: String?) {
        send(to, account.passwordResetCode(firstName, code, validMinutes, language))
    }

    override suspend fun sendEmailChangeCode(to: String, firstName: String, code: String, validMinutes: Int, language: String?) {
        send(to, account.emailChangeCode(firstName, code, validMinutes, language))
    }

    override suspend fun sendEmailChanged(to: String, firstName: String, newEmail: String, language: String?) {
        send(to, account.emailChanged(firstName, newEmail, language))
    }

    override suspend fun sendStaffInvite(to: String, email: StaffInviteEmail): Boolean {
        val letter = staffInviteText(email)
        if (sendsLocally) {
            logger.info("No RESEND_API_KEY locally: the invite to {} isn't emailed; its code is {}", to, email.code)
            return true
        }
        return send(to, letter)
    }

    override suspend fun sendPlanRequest(to: String, email: PlanRequestEmail): Boolean {
        // For the platform admins: always English.
        val owner = "${email.ownerName} (${email.ownerEmail})".escapeHtml()
        val content = paragraph("$owner asked for <strong>${email.requestedPlan.id}</strong> for their space <strong>${email.spaceName.escapeHtml()}</strong>, now on ${email.currentPlan.id}.") +
            note("""To set it: <code>PUT /v1/admin/spaces/${email.spaceId}/plan</code> with <code>{"plan": "${email.requestedPlan.id}"}</code>.""")
        return send(to, Email(
            subject = "Plan request: ${email.spaceName} wants ${email.requestedPlan.id}",
            html = EmailLayout.page(PRODUCT, language = null, content = content, footer = "Sent to the platform admins.")
        ))
    }

    private suspend fun send(to: String, email: Email): Boolean = sender.send(to = to, subject = email.subject, html = email.html)

    private fun notInTheCoachProduct(what: String) {
        logger.warn("A {} email was asked for on the coach server, which has none; not sent", what)
    }
}

/** An emailed invite: Portuguese for "pt", English otherwise. Names are escaped in the body (the subject is plain text). */
internal fun staffInviteText(email: StaffInviteEmail): Email {
    val owner = email.ownerName.escapeHtml()
    val ageGroup = email.ageGroupName.escapeHtml()
    val space = email.spaceName.escapeHtml()
    val until = email.expiresAt.toLocalDateTime(TimeZone.of("Europe/Lisbon")).date
    return if (isPortuguese(email.language)) {
        val date = "${until.day} de ${PT_MONTHS[until.month.ordinal]}"
        Email(
            subject = "${email.ownerName} convidou-te para o ${email.ageGroupName} no $PRODUCT",
            html = EmailLayout.page(
                PRODUCT, email.language,
                greeting("Olá,") +
                    paragraph("<strong>$owner</strong> convidou-te para fazeres parte da equipa técnica do <strong>$ageGroup</strong> ($space) no $PRODUCT. O teu código:") +
                    code(email.code) +
                    paragraph("Instala a app $PRODUCT, cria a tua conta (ou entra) e, no menu ☰, escolhe <strong>Entrar com um código</strong>. O código é válido até $date e só pode ser usado uma vez.") +
                    note("Se não estavas à espera deste convite, podes ignorar este email."),
                footer = "Recebes este email porque $owner te convidou no $PRODUCT."
            )
        )
    } else {
        val date = "${until.day} ${EN_MONTHS[until.month.ordinal]}"
        Email(
            subject = "${email.ownerName} invited you to ${email.ageGroupName} on $PRODUCT",
            html = EmailLayout.page(
                PRODUCT, email.language,
                greeting("Hi,") +
                    paragraph("<strong>$owner</strong> invited you to join the staff of <strong>$ageGroup</strong> ($space) on $PRODUCT. Your code:") +
                    code(email.code) +
                    paragraph("Install the $PRODUCT app, create your account (or sign in) and, in the ☰ menu, choose <strong>Join with a code</strong>. The code is valid until $date and can be used once.") +
                    note("If you weren't expecting this invite, you can ignore this email."),
                footer = "You're receiving this email because $owner invited you on $PRODUCT."
            )
        )
    }
}

private const val PRODUCT = "Squadify Coach"

private val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val PT_MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
