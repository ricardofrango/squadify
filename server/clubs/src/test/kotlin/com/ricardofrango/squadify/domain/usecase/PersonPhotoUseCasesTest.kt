package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.UpdatePlayerRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Photos of people: a player's (behind Edit player's gate) and a user's own. */
class PersonPhotoUseCasesTest {

    private class FakeStorage(override val isAvailable: Boolean = true, val failPut: Boolean = false) : ImageStorage {
        val files = mutableMapOf<String, ByteArray>()
        val log = mutableListOf<String>()
        override suspend fun put(key: String, bytes: ByteArray, contentType: String) {
            log += "put $key"
            if (failPut) error("bucket down")
            files[key] = bytes
        }
        override suspend fun get(key: String) = files[key]
        override suspend fun delete(key: String) { log += "delete $key"; files.remove(key) }
    }

    private val clubId = "club-1"
    private val playerId = "p-1"
    private val ag1 = "ag-1"
    private var player = Player(playerId, clubId, "Rui", "Costa", LocalDate(2013, 1, 1), active = true)
    private val users = mutableMapOf<String, User>()

    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    private val check = CheckUserPermissionUseCase(
        userRepository, mockk<TeamRepository>(), mockk<PlayerRepository>(relaxed = true), ageGroupRepository, transactionManager, clubPermissionRepository
    )

    init {
        coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null
        coEvery { playerRepository.getPlayerById(playerId) } answers { player }
        coEvery { playerRepository.getAgeGroupIdsByPlayer(playerId) } returns listOf(ag1)
        coEvery { playerRepository.updatePhotoUrl(playerId, any()) } answers { player = player.copy(photoUrl = secondArg()); true }
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(any(), any()) } returns null
        coEvery { userRepository.findById(any()) } answers { users[firstArg()] }
        coEvery { userRepository.updateAvatarUrl(any(), any()) } answers {
            users[firstArg()] = users.getValue(firstArg()).copy(avatarUrl = secondArg()); true
        }
    }

    private fun user(id: String, role: ClubRole?, ageGroupRole: AgeGroupRole? = null): String {
        users[id] = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.getUserRoleInClub(id, clubId) } returns role
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(id, ag1) } returns ageGroupRole
        return id
    }

    private val png: ByteArray = ByteArrayOutputStream().use { ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", it); it.toByteArray() }

    private fun uploadPhoto(storage: ImageStorage) = UploadPlayerPhotoUseCase(playerRepository, check, transactionManager, storage)
    private fun uploadAvatar(storage: ImageStorage) = UploadUserAvatarUseCase(userRepository, transactionManager, storage)

    @Test
    fun replacingAPlayersPhotoDeletesTheOldFileFirstThenStoresTheNewOneUnderANewId() = runBlocking {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)

        val first = uploadPhoto(storage)(coord, playerId, png)
        assertTrue(first.startsWith("/players/$playerId/photo/") && first.endsWith(".png"))
        storage.log.clear()
        val second = uploadPhoto(storage)(coord, playerId, png)

        assertNotEquals(first, second)
        assertEquals(listOf("delete ${first.removePrefix("/")}", "put ${second.removePrefix("/")}"), storage.log)
        assertEquals(second, player.photoUrl)
    }

    @Test
    fun thePlayersAgeGroupCoachCanChangeItAMemberCannot() = runBlocking<Unit> {
        val storage = FakeStorage()
        val coach = user("coach", ClubRole.MEMBER, AgeGroupRole.COACH)
        val url = uploadPhoto(storage)(coach, playerId, png)
        assertEquals(url, player.photoUrl)

        val member = user("member", ClubRole.MEMBER)
        storage.log.clear()
        assertFailsWith<ForbiddenException> { uploadPhoto(storage)(member, playerId, png) }
        assertFailsWith<ForbiddenException> { DeletePlayerPhotoUseCase(playerRepository, check, transactionManager, storage)(member, playerId) }
        assertTrue(storage.log.isEmpty())
    }

    @Test
    fun aBadImageLeavesThePhotoAloneAndAFailedStoreClearsIt() = runBlocking<Unit> {
        val coord = user("coord", ClubRole.COORDINATOR)
        val storage = FakeStorage()
        val current = uploadPhoto(storage)(coord, playerId, png)
        assertFailsWith<ValidationException> { uploadPhoto(storage)(coord, playerId, "nope".toByteArray()) }
        assertEquals(current, player.photoUrl)

        assertFailsWith<IllegalStateException> { uploadPhoto(FakeStorage(failPut = true))(coord, playerId, png) }
        assertNull(player.photoUrl)
    }

    @Test
    fun withoutStorageUploadsAreRefused() = runBlocking<Unit> {
        assertFailsWith<ValidationException> { uploadPhoto(FakeStorage(isAvailable = false))(user("coord", ClubRole.COORDINATOR), playerId, png) }
        assertFailsWith<ValidationException> { uploadAvatar(FakeStorage(isAvailable = false))(user("me", null), png) }
    }

    @Test
    fun clubMembersGetOnlyThePlayersCurrentPhotoOutsidersNothing() = runBlocking<Unit> {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        val old = uploadPhoto(storage)(coord, playerId, png).substringAfterLast('/').removeSuffix(".png")
        val current = uploadPhoto(storage)(coord, playerId, png).substringAfterLast('/').removeSuffix(".png")
        val get = GetPlayerPhotoUseCase(playerRepository, check, transactionManager, storage)
        val member = user("member", ClubRole.MEMBER)

        assertContentEquals(storage.files.values.single(), get(member, playerId, current))
        assertFailsWith<NotFoundException> { get(member, playerId, old) }
        assertFailsWith<ForbiddenException> { get(user("outsider", null), playerId, current) }
    }

    @Test
    fun deletingAPlayersPhotoRemovesTheFileAndTheLink() = runBlocking {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        uploadPhoto(storage)(coord, playerId, png)
        DeletePlayerPhotoUseCase(playerRepository, check, transactionManager, storage)(coord, playerId)
        assertTrue(storage.files.isEmpty())
        assertNull(player.photoUrl)
    }

    @Test
    fun aPhotoUrlSentWhenEditingThePlayerIsIgnored() = runBlocking {
        val coord = user("coord", ClubRole.COORDINATOR)
        player = player.copy(photoUrl = "/players/$playerId/photo/00000000-0000-0000-0000-000000000001.png")
        val updated = slot<Player>()
        coEvery { playerRepository.updatePlayer(capture(updated)) } returns true
        UpdatePlayerUseCase(playerRepository, userRepository, mockk(relaxed = true), mockk(relaxed = true), ageGroupRepository, transactionManager, check)(
            coord, playerId, UpdatePlayerRequest(firstName = "Rui", photoUrl = "https://example.com/x.png")
        )
        assertEquals(player.photoUrl, updated.captured.photoUrl)
    }

    @Test
    fun aUserReplacesAndRemovesTheirOwnPhoto() = runBlocking {
        val storage = FakeStorage()
        val me = user("me", null)

        val first = uploadAvatar(storage)(me, png)
        assertTrue(first.startsWith("/users/$me/avatar/") && first.endsWith(".png"))
        val second = uploadAvatar(storage)(me, png)
        assertEquals(setOf(second.removePrefix("/")), storage.files.keys)
        assertEquals(second, users.getValue(me).avatarUrl)

        DeleteUserAvatarUseCase(userRepository, transactionManager, storage)(me)
        assertTrue(storage.files.isEmpty())
        assertNull(users.getValue(me).avatarUrl)
    }

    @Test
    fun onlyAUsersCurrentPhotoIsServed() = runBlocking<Unit> {
        val storage = FakeStorage()
        val me = user("me", null)
        val old = uploadAvatar(storage)(me, png).substringAfterLast('/').removeSuffix(".png")
        val current = uploadAvatar(storage)(me, png).substringAfterLast('/').removeSuffix(".png")
        val get = GetUserAvatarUseCase(userRepository, transactionManager, storage)

        assertContentEquals(storage.files.values.single(), get(me, current))
        assertFailsWith<NotFoundException> { get(me, old) }
        assertFailsWith<NotFoundException> { get("nobody", current) }
    }

    @Test
    fun aPhotoUrlThatIsntOursIsNeverTurnedIntoAStorageKey() {
        assertNull(PlayerPhotos.keyOf(playerId, "https://example.com/p.png"))
        assertNull(UserAvatars.keyOf("me", "/users/me/avatar/../../etc.png"))
        assertNull(UserAvatars.keyOf("me", "/users/other/avatar/00000000-0000-0000-0000-000000000001.png"))
    }
}
