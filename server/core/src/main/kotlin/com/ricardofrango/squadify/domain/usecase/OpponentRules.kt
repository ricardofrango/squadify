package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.OPPONENT_SHORT_NAME_MAX
import com.ricardofrango.squadify.model.Opponent

// Rules shared by creating and editing an opponent (design 38e).

/** Trimmed, or null when blank: an optional text field left empty. */
fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** The name, trimmed; it can't be blank. */
fun requireOpponentName(name: String): String =
    name.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("Opponent name is required")

/** The short name, trimmed and uppercased, at most [OPPONENT_SHORT_NAME_MAX] characters; null when blank. */
fun normalizeShortName(shortName: String?): String? {
    val value = shortName.cleaned()?.uppercase() ?: return null
    if (value.length > OPPONENT_SHORT_NAME_MAX) {
        throw ValidationException("Short name can have at most $OPPONENT_SHORT_NAME_MAX characters")
    }
    return value
}

/** Two opponents of the same club can't share a name, ignoring case. [exceptId] is the one being edited. */
fun requireUniqueOpponentName(name: String, clubOpponents: List<Opponent>, exceptId: String? = null) {
    if (clubOpponents.any { it.id != exceptId && it.name.trim().equals(name, ignoreCase = true) }) {
        throw ConflictException("An opponent with this name already exists")
    }
}
