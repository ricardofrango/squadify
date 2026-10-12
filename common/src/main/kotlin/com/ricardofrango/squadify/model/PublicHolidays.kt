package com.ricardofrango.squadify.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

// National public holidays, for creating training sessions (design 39c/d): sessions are skipped on them
// unless "Also on public holidays" is on. Municipal holidays are deliberately not considered.

/** A club's country, for its public holidays. Stored as its ISO code. */
@Serializable
enum class Country(val code: String) {
    PORTUGAL("PT");

    companion object {
        val DEFAULT = PORTUGAL
        fun fromCode(code: String?): Country = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/** Which holiday it is; the app shows its localized name. */
@Serializable
enum class Holiday {
    NEW_YEAR, GOOD_FRIDAY, EASTER, FREEDOM_DAY, LABOUR_DAY, CORPUS_CHRISTI, PORTUGAL_DAY,
    ASSUMPTION, REPUBLIC_DAY, ALL_SAINTS, RESTORATION_OF_INDEPENDENCE, IMMACULATE_CONCEPTION, CHRISTMAS
}

@Serializable
data class PublicHoliday(val date: LocalDate, val holiday: Holiday)

/** Easter Sunday of [year] (the Gregorian "anonymous" algorithm). */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate(year, month, day)
}

/** [country]'s national public holidays in [year], by date. */
fun publicHolidays(country: Country, year: Int): List<PublicHoliday> = when (country) {
    Country.PORTUGAL -> {
        val easter = easterSunday(year)
        listOf(
            PublicHoliday(LocalDate(year, 1, 1), Holiday.NEW_YEAR),
            PublicHoliday(easter.plus(DatePeriod(days = -2)), Holiday.GOOD_FRIDAY),
            PublicHoliday(easter, Holiday.EASTER),
            PublicHoliday(LocalDate(year, 4, 25), Holiday.FREEDOM_DAY),
            PublicHoliday(LocalDate(year, 5, 1), Holiday.LABOUR_DAY),
            PublicHoliday(easter.plus(DatePeriod(days = 60)), Holiday.CORPUS_CHRISTI),
            PublicHoliday(LocalDate(year, 6, 10), Holiday.PORTUGAL_DAY),
            PublicHoliday(LocalDate(year, 8, 15), Holiday.ASSUMPTION),
            PublicHoliday(LocalDate(year, 10, 5), Holiday.REPUBLIC_DAY),
            PublicHoliday(LocalDate(year, 11, 1), Holiday.ALL_SAINTS),
            PublicHoliday(LocalDate(year, 12, 1), Holiday.RESTORATION_OF_INDEPENDENCE),
            PublicHoliday(LocalDate(year, 12, 8), Holiday.IMMACULATE_CONCEPTION),
            PublicHoliday(LocalDate(year, 12, 25), Holiday.CHRISTMAS)
        ).sortedBy { it.date }
    }
}

/** The holidays between [from] and [to] (inclusive), keyed by date. */
fun publicHolidaysBetween(country: Country, from: LocalDate, to: LocalDate): Map<LocalDate, Holiday> =
    (from.year..to.year)
        .flatMap { publicHolidays(country, it) }
        .filter { it.date in from..to }
        .associate { it.date to it.holiday }
