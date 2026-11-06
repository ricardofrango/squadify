package com.ricardofrango.squadify.email

/**
 * Every email's look, both servers' (TECHNICAL_DECISIONS §72): the app's carbon header with its mark
 * (the 3-3-4 green dots, drawn in HTML so there's no image to host or for a mail app to block) and the
 * product's name, a white card with the text, and a grey line under it. Inline styles and tables only,
 * which is what mail apps keep. [language] picks the page's `lang`; the texts come already written in it.
 */
object EmailLayout {
    private const val FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"
    private const val CARBON = "#1C1C1E"
    private const val INK = "#121212"
    private const val GREY = "#6B6B70"
    private const val BACKGROUND = "#F5F5F5"
    private const val GREEN = "#2E7D32"
    private const val LIGHT_GREEN = "#4CAF50"

    /** The whole email: [product] ("Squadify", "Squadify Coach") in the header, [content] in the card, [footer] under it. */
    fun page(product: String, language: String?, content: String, footer: String): String = """
        <!DOCTYPE html>
        <html lang="${if (isPortuguese(language)) "pt" else "en"}">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;padding:0;background:$BACKGROUND;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:$BACKGROUND;">
          <tr><td align="center" style="padding:28px 12px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;">
              <tr><td align="center" style="background:$CARBON;border-radius:20px 20px 0 0;padding:28px 24px 22px;">
                ${mark()}
                <div style="font-family:$FONT;font-size:24px;font-weight:800;letter-spacing:1px;color:#FFFFFF;margin-top:12px;">${product.escapeHtml()}</div>
              </td></tr>
              <tr><td style="background:#FFFFFF;border-radius:0 0 20px 20px;padding:30px 28px 32px;font-family:$FONT;font-size:16px;line-height:1.55;color:$INK;">
                $content
              </td></tr>
              <tr><td align="center" style="padding:18px 12px 0;font-family:$FONT;font-size:12px;line-height:1.5;color:$GREY;">$footer</td></tr>
            </table>
          </td></tr>
        </table>
        </body>
        </html>
    """.trimIndent()

    /** A paragraph of the card. [html] is already escaped where it holds names. */
    fun paragraph(html: String): String = """<p style="margin:0 0 16px;">$html</p>"""

    /** The card's first line, bold: "Olá Ana," / "Hi Ana,". */
    fun greeting(html: String): String = """<p style="margin:0 0 16px;font-size:18px;font-weight:800;">$html</p>"""

    /** A 6-digit code, big and spaced on a grey box, easy to read and copy. */
    fun code(code: String): String =
        """<div style="margin:4px 0 20px;padding:18px 12px;background:$BACKGROUND;border-radius:14px;text-align:center;font-family:$FONT;font-size:32px;font-weight:800;letter-spacing:8px;color:$INK;">${code.escapeHtml()}</div>"""

    /** A green button to a page. */
    fun button(label: String, url: String): String =
        """<p style="margin:4px 0 20px;"><a href="${url.escapeHtml()}" style="display:inline-block;background:$GREEN;color:#FFFFFF;font-family:$FONT;font-size:16px;font-weight:800;text-decoration:none;padding:14px 24px;border-radius:14px;">${label.escapeHtml()}</a></p>"""

    /** The small grey line at the end of the card ("If you didn't ask for this, …"). */
    fun note(html: String): String = """<p style="margin:0;font-size:14px;color:$GREY;">$html</p>"""

    // The app's mark (SquadifyMark): three light dots, three dark ones inset, four dark ones; each row a
    // centred table, the rows the same width but the middle one.
    private fun mark(): String {
        fun row(dots: Int, gap: Int, color: String, last: Boolean = false): String {
            val cells = (0 until dots).joinToString("""<td width="$gap" style="font-size:0;line-height:0;">&nbsp;</td>""") {
                """<td width="10" height="10" style="width:10px;height:10px;background:$color;border-radius:5px;font-size:0;line-height:0;">&nbsp;</td>"""
            }
            val space = if (last) "" else "margin-bottom:8px;"
            return """<table role="presentation" cellpadding="0" cellspacing="0" align="center" style="$space"><tr>$cells</tr></table>"""
        }
        return row(3, 20, LIGHT_GREEN) + row(3, 9, GREEN) + row(4, 10, GREEN, last = true)
    }
}

/** Portuguese for "pt…" (the app's language), English otherwise. */
fun isPortuguese(language: String?): Boolean = language?.trim()?.lowercase()?.startsWith("pt") == true

/** For a name or address put into an email's HTML. */
fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
