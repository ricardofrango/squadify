package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.EmailService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
private data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String
)

/**
 * Sends via Resend's API (https://resend.com/docs/api-reference/emails/send-email). Until a real
 * sending domain is verified, Resend only allows [ServerConfig.emailFromAddress]'s default
 * (onboarding@resend.dev) to deliver to the Resend account's own verified email -- see
 * TECHNICAL_DECISIONS.md.
 */
class ResendEmailService : EmailService {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

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

    private suspend fun send(to: String, subject: String, html: String) {
        if (ServerConfig.resendApiKey.isBlank()) {
            logger.warn("RESEND_API_KEY not set -- skipping email to {} ({})", to, subject)
            return
        }
        try {
            val response = client.post("https://api.resend.com/emails") {
                header("Authorization", "Bearer ${ServerConfig.resendApiKey}")
                contentType(ContentType.Application.Json)
                setBody(
                    ResendEmailRequest(
                        from = ServerConfig.emailFromAddress,
                        to = listOf(to),
                        subject = subject,
                        html = html
                    )
                )
            }
            if (!response.status.isSuccess()) {
                logger.error("Resend send to {} failed: {} {}", to, response.status, response.bodyAsText())
            }
        } catch (e: Exception) {
            logger.error("Resend send to {} threw", to, e)
        }
    }
}
