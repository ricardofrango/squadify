package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.model.SearchText
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.append

/**
 * "first last" lowercase and without accents, in SQL: the same folding as [SearchText.key], so a
 * search on the server matches what the app's own searches match ("joao" finds "João").
 */
internal fun searchKeyOf(firstName: Column<String>, lastName: Column<String>): Expression<String> =
    CustomFunction(
        "translate", TextColumnType(),
        concat(" ", listOf(firstName, lastName)).lowerCase(),
        stringLiteral(SearchText.ACCENTED),
        stringLiteral(SearchText.UNACCENTED)
    )

/** [text] lowercase and without accents, as [searchKeyOf] folds a name (null stays null). */
internal fun <T : String?> searchKeyOf(text: Expression<T>): Expression<String> =
    CustomFunction("translate", TextColumnType(), text.lowerCase(), stringLiteral(SearchText.ACCENTED), stringLiteral(SearchText.UNACCENTED))

/**
 * Ordered character by character, as Kotlin compares strings, whatever the database's collation (which
 * may skip spaces and punctuation): rows paged by it come in the order the app sorts them in.
 */
internal fun Expression<String>.byCodePoint(): Expression<String> = CodePointOrder(this)

private class CodePointOrder(private val expr: Expression<String>) : Expression<String>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(expr, " COLLATE \"C\"") }
}

/** This key contains [search], which must already be folded with [SearchText.key]. */
internal fun Expression<String>.containsSearch(search: String): Op<Boolean> {
    val escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return this like "%$escaped%"
}
