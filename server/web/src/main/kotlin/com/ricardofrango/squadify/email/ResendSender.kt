package com.ricardofrango.squadify.email

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Sends one email through Resend's API (https://resend.com/docs/api-reference/emails/send-email), for
 * both servers' EmailService (TECHNICAL_DECISIONS §72): each passes its own API key and from address
 * ("Squadify <…>", "Squadify Coach <…>") and writes its own texts.
 *
 * Never throws: an email is a best-effort notice, so a missing key only logs and skips, and a failed
 * send is logged.
 */
class ResendSender(private val apiKey: String, private val fromAddress: String) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient(CIO)

    suspend fun send(to: String, subject: String, html: String) {
        if (apiKey.isBlank()) {
            logger.warn("RESEND_API_KEY not set -- skipping email to {} ({})", to, subject)
            return
        }
        try {
            val body = buildJsonObject {
                put("from", fromAddress)
                put("to", buildJsonArray { add(JsonPrimitive(to)) })
                put("subject", subject)
                put("html", html)
            }
            val response = client.post("https://api.resend.com/emails") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) {
                logger.error("Resend send to {} failed: {} {}", to, response.status, response.bodyAsText())
            }
        } catch (e: Exception) {
            logger.error("Resend send to {} threw", to, e)
        }
    }
}
