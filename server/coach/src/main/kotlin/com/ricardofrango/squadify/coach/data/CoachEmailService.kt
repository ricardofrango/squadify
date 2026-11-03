package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.domain.PlanRequestEmail
import com.ricardofrango.squadify.coach.domain.PlanRequestMailer
import com.ricardofrango.squadify.coach.domain.StaffInviteEmail
import com.ricardofrango.squadify.coach.domain.StaffInviteMailer
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.email.ResendSender
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * The coach server's emails (TECHNICAL_DECISIONS §72): the account's codes and notices, as "Squadify
 * Coach", through the shared [ResendSender]. In English for now, like the clubs server's; the user's
 * language and a designed template with the app's logo come before the launch (REQUIREMENTS, launch
 * readiness).
 *
 * The coach product has no clubs and adds staff with codes, so the club and age-group emails are never
 * asked for here; they only log. A code can be emailed ([sendStaffInvite]), in the owner's app language
 * (English or Portuguese): the first coach email that isn't English only. A plan request goes to the
 * platform admins ([sendPlanRequest]), in English.
 */
class CoachEmailService(
    private val sender: ResendSender = ResendSender(CoachConfig.resendApiKey, CoachConfig.emailFromAddress)
) : EmailService, StaffInviteMailer, PlanRequestMailer {
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

    override suspend fun sendStaffInvite(to: String, email: StaffInviteEmail): Boolean {
        val (subject, html) = staffInviteText(email)
        return sender.send(to = to, subject = subject, html = html)
    }

    override suspend fun sendPlanRequest(to: String, email: PlanRequestEmail): Boolean {
        val owner = "${email.ownerName} (${email.ownerEmail})".escapeHtml()
        return sender.send(
            to = to,
            subject = "Plan request: ${email.spaceName} wants ${email.requestedPlan.id}",
            html = """
                <p>$owner asked for <strong>${email.requestedPlan.id}</strong> for their space <strong>${email.spaceName.escapeHtml()}</strong>, now on ${email.currentPlan.id}.</p>
                <p>To set it: <code>PUT /v1/admin/spaces/${email.spaceId}/plan</code> with <code>{"plan": "${email.requestedPlan.id}"}</code>.</p>
            """.trimIndent()
        )
    }

    private fun notInTheCoachProduct(what: String) {
        logger.warn("A {} email was asked for on the coach server, which has none; not sent", what)
    }
}

/** An emailed invite's subject and body: Portuguese for "pt", English otherwise. Names are escaped in the body (the subject is plain text). */
internal fun staffInviteText(email: StaffInviteEmail): Pair<String, String> {
    val owner = email.ownerName.escapeHtml()
    val ageGroup = email.ageGroupName.escapeHtml()
    val space = email.spaceName.escapeHtml()
    val until = email.expiresAt.toLocalDateTime(TimeZone.of("Europe/Lisbon")).date
    val codeBlock = """<p style="font-size:28px;font-weight:bold;letter-spacing:6px">${email.code}</p>"""
    return if (email.language?.lowercase()?.startsWith("pt") == true) {
        val date = "${until.day} de ${PT_MONTHS[until.month.ordinal]}"
        "${email.ownerName} convidou-te para o ${email.ageGroupName} no Squadify Coach" to """
            <p>Olá,</p>
            <p><strong>$owner</strong> convidou-te para fazeres parte da equipa técnica do <strong>$ageGroup</strong> ($space) no Squadify Coach.</p>
            <p>O teu código:</p>
            $codeBlock
            <p>Instala a app Squadify Coach, cria a tua conta (ou entra) e, no menu ☰, escolhe <strong>Entrar com um código</strong>. O código é válido até $date e só pode ser usado uma vez.</p>
            <p>Se não estavas à espera deste convite, podes ignorar este email.</p>
        """.trimIndent()
    } else {
        val date = "${until.day} ${EN_MONTHS[until.month.ordinal]}"
        "${email.ownerName} invited you to ${email.ageGroupName} on Squadify Coach" to """
            <p>Hi,</p>
            <p><strong>$owner</strong> invited you to join the staff of <strong>$ageGroup</strong> ($space) on Squadify Coach.</p>
            <p>Your code:</p>
            $codeBlock
            <p>Install the Squadify Coach app, create your account (or sign in) and, in the ☰ menu, choose <strong>Join with a code</strong>. The code is valid until $date and can be used once.</p>
            <p>If you weren't expecting this invite, you can ignore this email.</p>
        """.trimIndent()
    }
}

private val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val PT_MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
