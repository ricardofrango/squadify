package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.components.PlayerBadgeContent
import kotlin.test.Test
import kotlin.test.assertEquals

// A player row's badge: the photo with the number under it, or whichever one they have, or a placeholder.
class PlayerBadgeTest {
    private val photo = "/players/p/photo/a.png"

    @Test
    fun aPhotoAndANumberShowBoth() = assertEquals(PlayerBadgeContent.PHOTO_AND_NUMBER, PlayerBadgeContent.of(photo, 10))

    @Test
    fun onlyAPhotoShowsThePhoto() = assertEquals(PlayerBadgeContent.PHOTO, PlayerBadgeContent.of(photo, null))

    @Test
    fun onlyANumberShowsTheNumber() = assertEquals(PlayerBadgeContent.NUMBER, PlayerBadgeContent.of(null, 7))

    @Test
    fun neitherShowsThePlaceholder() = assertEquals(PlayerBadgeContent.PLACEHOLDER, PlayerBadgeContent.of(null, null))
}
