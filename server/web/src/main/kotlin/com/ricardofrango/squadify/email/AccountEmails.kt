package com.ricardofrango.squadify.email

import com.ricardofrango.squadify.email.EmailLayout.code
import com.ricardofrango.squadify.email.EmailLayout.greeting
import com.ricardofrango.squadify.email.EmailLayout.note
import com.ricardofrango.squadify.email.EmailLayout.paragraph

/** An email ready to send: its subject (plain text) and its HTML. */
data class Email(val subject: String, val html: String)

/**
 * The account's emails, the same on both servers but for the product's name: the password reset code,
 * the email change code and the notice to the old address. In English or Portuguese, the language of
 * the app that asked ([isPortuguese]). [informal]: the coach app's Portuguese ("tu"); the clubs app's is
 * formal ("você"), like each app's own text. [askTheClub]: the clubs product, where the notice also
 * says to tell the club.
 */
class AccountEmails(private val product: String, private val informal: Boolean, private val askTheClub: Boolean) {

    fun passwordResetCode(firstName: String, code: String, validMinutes: Int, language: String?): Email {
        val name = firstName.escapeHtml()
        return if (isPortuguese(language)) Email(
            subject = "${if (informal) "O teu" else "O seu"} código para redefinir a senha do $product: $code",
            html = page(language, greeting("Olá $name,") +
                paragraph(if (informal) "Usa este código no $product para definires uma nova senha:" else "Use este código no $product para definir uma nova senha:") +
                code(code) +
                note(
                    if (informal) "Expira dentro de $validMinutes minutos. Se não pediste para redefinir a senha, podes ignorar este email: a tua senha não mudou."
                    else "Expira dentro de $validMinutes minutos. Se não pediu para redefinir a senha, pode ignorar este email: a sua senha não mudou."
                ))
        ) else Email(
            subject = "Your $product password reset code: $code",
            html = page(language, greeting("Hi $name,") +
                paragraph("Use this code in $product to set a new password:") +
                code(code) +
                note("It expires in $validMinutes minutes. If you didn't ask to reset your password, you can ignore this email: your password hasn't changed."))
        )
    }

    fun emailChangeCode(firstName: String, code: String, validMinutes: Int, language: String?): Email {
        val name = firstName.escapeHtml()
        return if (isPortuguese(language)) Email(
            subject = "${if (informal) "O teu" else "O seu"} código para alterar o email do $product: $code",
            html = page(language, greeting("Olá $name,") +
                paragraph(
                    if (informal) "Usa este código no $product para confirmares este endereço como o teu novo email de acesso:"
                    else "Use este código no $product para confirmar este endereço como o seu novo email de acesso:"
                ) +
                code(code) +
                note(
                    if (informal) "Expira dentro de $validMinutes minutos. Se não pediste esta alteração, podes ignorar este email."
                    else "Expira dentro de $validMinutes minutos. Se não pediu esta alteração, pode ignorar este email."
                ))
        ) else Email(
            subject = "Your $product email change code: $code",
            html = page(language, greeting("Hi $name,") +
                paragraph("Use this code in $product to confirm this address as your new sign-in email:") +
                code(code) +
                note("It expires in $validMinutes minutes. If you didn't ask for this, you can ignore this email."))
        )
    }

    fun emailChanged(firstName: String, newEmail: String, language: String?): Email {
        val name = firstName.escapeHtml()
        val address = "<strong>${newEmail.escapeHtml()}</strong>"
        return if (isPortuguese(language)) Email(
            subject = "O email ${if (informal) "da tua" else "da sua"} conta $product foi alterado",
            html = page(language, greeting("Olá $name,") +
                paragraph(
                    if (informal) "O email com que entras no $product passou a ser $address. A partir de agora, entra com esse endereço."
                    else "O email com que entra no $product passou a ser $address. A partir de agora, entre com esse endereço."
                ) +
                note(
                    when {
                        informal -> "Se não foste tu que fizeste esta alteração, responde a este email quanto antes."
                        askTheClub -> "Se não foi você que fez esta alteração, responda a este email ou fale com o seu clube quanto antes."
                        else -> "Se não foi você que fez esta alteração, responda a este email quanto antes."
                    }
                ))
        ) else Email(
            subject = "Your $product email was changed",
            html = page(language, greeting("Hi $name,") +
                paragraph("The email you sign in to $product with was changed to $address. From now on, sign in with that address.") +
                note(
                    if (askTheClub) "If you didn't make this change, reply to this email or contact your club straight away."
                    else "If you didn't make this change, reply to this email straight away."
                ))
        )
    }

    private fun page(language: String?, content: String): String {
        val footer = when {
            !isPortuguese(language) -> "You're receiving this email because of your $product account."
            informal -> "Recebes este email por causa da tua conta $product."
            else -> "Recebe este email por causa da sua conta $product."
        }
        return EmailLayout.page(product, language, content, footer)
    }
}
