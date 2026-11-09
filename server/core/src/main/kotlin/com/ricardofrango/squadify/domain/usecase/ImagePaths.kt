package com.ricardofrango.squadify.domain.usecase

import kotlin.uuid.Uuid

/**
 * Where an uploaded image lives. Each upload gets a new image id, so the path changes with the image
 * and an app can cache it forever: `{kind}/{ownerId}/{name}/{imageId}.png` in storage, and the same
 * path under the API (`/{kind}/{ownerId}/{name}/{imageId}.png`) in the owner's URL column.
 * See TECHNICAL_DECISIONS.md §57.
 */
class ImagePaths(private val kind: String, private val name: String) {
    fun key(ownerId: String, imageId: String) = "$kind/$ownerId/$name/$imageId.png"
    fun url(ownerId: String, imageId: String) = "/${key(ownerId, imageId)}"

    /** The storage key behind an owner's image URL, or null when it isn't one of ours. */
    fun keyOf(ownerId: String, imageUrl: String): String? {
        val imageId = imageUrl.removePrefix("/$kind/$ownerId/$name/").removeSuffix(".png")
        if (imageId == imageUrl || imageId.isEmpty()) return null
        return runCatching { Uuid.parse(imageId) }.getOrNull()?.let { key(ownerId, imageId) }
    }
}

/** A club's logo (`clubs.logo_url`). */
val ClubLogos = ImagePaths("clubs", "logo")

/** An age group's crest, the club it plays for (`age_groups.logo_url`). */
val AgeGroupLogos = ImagePaths("age-groups", "logo")

/** An opponent's logo (`opponents.logo_url`). */
val OpponentLogos = ImagePaths("opponents", "logo")

/** A player's photo (`players.photo_url`). */
val PlayerPhotos = ImagePaths("players", "photo")

/** A user's own profile photo (`users.avatar_url`). */
val UserAvatars = ImagePaths("users", "avatar")
