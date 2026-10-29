package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.Player
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** A scorer's photo in a goal push: a minor's, so only behind a signed link that runs out. */
class PublicImageLinksTest {

    private var now = Instant.fromEpochSeconds(1_000)
    private val clock = object : Clock { override fun now() = this@PublicImageLinksTest.now }
    private val links = PublicImageLinks("https://api.example.com", "secret", clock)
    private val imageId = "11111111-1111-1111-1111-111111111111"
    private val photoUrl = "/players/p-1/photo/$imageId.png"

    private fun sigOf(url: String) = url.substringAfter("sig=")
    private fun expOf(url: String) = url.substringAfter("exp=").substringBefore("&").toLong()

    @Test
    fun aPhotoLinkWorksUntilItRunsOut() {
        val url = links.playerPhoto("p-1", photoUrl)!!
        assertTrue(links.isValidPhotoLink("p-1", imageId, expOf(url), sigOf(url)))
        now = Instant.fromEpochSeconds(expOf(url) + 1)
        assertFalse(links.isValidPhotoLink("p-1", imageId, expOf(url), sigOf(url)))
    }

    @Test
    fun aLinkCantBeReusedForAnotherPlayerAnotherPhotoOrALaterExpiry() {
        val url = links.playerPhoto("p-1", photoUrl)!!
        assertFalse(links.isValidPhotoLink("p-2", imageId, expOf(url), sigOf(url)))
        assertFalse(links.isValidPhotoLink("p-1", "22222222-2222-2222-2222-222222222222", expOf(url), sigOf(url)))
        assertFalse(links.isValidPhotoLink("p-1", imageId, expOf(url) + 3600, sigOf(url)))
        // Another server's secret signs differently.
        assertFalse(PublicImageLinks("https://api.example.com", "other", clock).isValidPhotoLink("p-1", imageId, expOf(url), sigOf(url)))
    }

    @Test
    fun noLinkWithoutAPublicAddressOrAPhotoOfOurs() {
        assertNull(PublicImageLinks(null, "secret", clock).playerPhoto("p-1", photoUrl))
        assertNull(links.playerPhoto("p-1", null))
        assertNull(links.playerPhoto("p-1", "https://elsewhere.com/x.png"))
        assertNull(links.opponentLogo("o-1", null))
    }

    @Test
    fun thePublicPhotoIsOnlyServedWithAValidSignatureAndForTheCurrentPhoto() = runBlocking {
        val playerRepository = mockk<PlayerRepository>()
        val storage = mockk<ImageStorage>()
        val tx = object : TransactionManager { override suspend fun <T> runInTransaction(block: suspend () -> T): T = block() }
        coEvery { playerRepository.getPlayerById("p-1") } returns Player("p-1", "c-1", "R", "C", LocalDate(2013, 1, 1), photoUrl = photoUrl, active = true)
        coEvery { storage.get(PlayerPhotos.key("p-1", imageId)) } returns byteArrayOf(1, 2, 3)
        val useCase = GetPublicPlayerPhotoUseCase(playerRepository, tx, storage, links)
        val url = links.playerPhoto("p-1", photoUrl)!!

        assertContentEquals(byteArrayOf(1, 2, 3), useCase("p-1", imageId, expOf(url), sigOf(url)))
        assertFailsWith<NotFoundException> { useCase("p-1", imageId, expOf(url), "forged") }
        assertFailsWith<NotFoundException> { useCase("p-1", imageId, null, null) }
        // A photo replaced since: the old image id is gone even with a valid link.
        coEvery { playerRepository.getPlayerById("p-1") } returns Player("p-1", "c-1", "R", "C", LocalDate(2013, 1, 1), photoUrl = "/players/p-1/photo/33333333-3333-3333-3333-333333333333.png", active = true)
        assertFailsWith<NotFoundException> { useCase("p-1", imageId, expOf(url), sigOf(url)) }
        Unit
    }
}
