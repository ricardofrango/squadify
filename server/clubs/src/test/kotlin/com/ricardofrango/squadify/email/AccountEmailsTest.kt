package com.ricardofrango.squadify.email

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The account emails (shared by both servers' EmailService) and the layout they go in. */
class AccountEmailsTest {
    private val clubs = AccountEmails("Squadify", informal = false, askTheClub = true)
    private val coach = AccountEmails("Squadify Coach", informal = true, askTheClub = false)

    @Test
    fun theCodeEmailsAreInTheAppsLanguageAndRegister() {
        val pt = clubs.passwordResetCode("Ana", "123456", 15, "pt-PT")
        assertEquals("O seu código para redefinir a senha do Squadify: 123456", pt.subject)
        assertTrue("Use este código" in pt.html && "123456" in pt.html && "15 minutos" in pt.html && """lang="pt"""" in pt.html)

        val coachPt = coach.emailChangeCode("Ana", "654321", 15, "pt")
        assertEquals("O teu código para alterar o email do Squadify Coach: 654321", coachPt.subject)
        assertTrue("Usa este código" in coachPt.html)

        val en = clubs.passwordResetCode("Ana", "123456", 15, "en-GB")
        assertEquals("Your Squadify password reset code: 123456", en.subject)
        assertTrue("Use this code in Squadify" in en.html && """lang="en"""" in en.html)
    }

    @Test
    fun noLanguageMeansEnglish() {
        assertEquals("Your Squadify Coach email was changed", coach.emailChanged("Ana", "new@example.com", null).subject)
    }

    @Test
    fun onlyTheClubsNoticeSaysToTellTheClub() {
        assertTrue("contact your club" in clubs.emailChanged("Ana", "new@example.com", "en").html)
        assertFalse("club" in coach.emailChanged("Ana", "new@example.com", "en").html)
        assertTrue("fale com o seu clube" in clubs.emailChanged("Ana", "new@example.com", "pt").html)
    }

    @Test
    fun namesAndAddressesAreEscaped() {
        val email = clubs.emailChanged("Ana <b>", "a&b@example.com", "en")
        assertTrue("Ana &lt;b&gt;" in email.html && "a&amp;b@example.com" in email.html)
        assertFalse("Ana <b>" in email.html)
    }

    @Test
    fun theLayoutHasTheProductAndTheMarksTenDots() {
        val html = EmailLayout.page("Squadify Coach", "pt", content = "<p>x</p>", footer = "f")
        assertTrue("Squadify Coach" in html)
        assertEquals(10, Regex("border-radius:5px").findAll(html).count())
    }
}
