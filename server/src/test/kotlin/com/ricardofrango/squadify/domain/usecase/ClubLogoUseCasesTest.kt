package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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

class ClubLogoUseCasesTest {

    /** In-memory storage that records the order of operations. */
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
    private var club = Club(clubId, "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    private val check = CheckUserPermissionUseCase(userRepository, mockk<TeamRepository>(), mockk<PlayerRepository>(relaxed = true), mockk<AgeGroupRepository>(), transactionManager, clubPermissionRepository)

    init {
        coEvery { clubRepository.getClubById(clubId) } answers { club }
        coEvery { clubRepository.updateLogoUrl(clubId, any()) } answers { club = club.copy(logoUrl = secondArg()); true }
    }

    private fun user(id: String, role: ClubRole?): String {
        coEvery { userRepository.findById(id) } returns User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.getUserRoleInClub(id, clubId) } returns role
        return id
    }

    private val png: ByteArray = ByteArrayOutputStream().use { ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", it); it.toByteArray() }

    private fun upload(storage: ImageStorage) = UploadClubLogoUseCase(clubRepository, check, transactionManager, storage)

    @Test
    fun replacingALogoDeletesTheOldFileFirstThenStoresTheNewOneUnderANewId() = runBlocking {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)

        val first = upload(storage)(coord, clubId, png)
        val firstKey = first.removePrefix("/")
        assertTrue(first.startsWith("/clubs/$clubId/logo/") && first.endsWith(".png"))
        assertEquals(first, club.logoUrl)

        storage.log.clear()
        val second = upload(storage)(coord, clubId, png)

        assertNotEquals(first, second)
        assertEquals(listOf("delete $firstKey", "put ${second.removePrefix("/")}"), storage.log)
        assertEquals(setOf(second.removePrefix("/")), storage.files.keys)
        assertEquals(second, club.logoUrl)
    }

    @Test
    fun aBadImageLeavesTheCurrentLogoAlone() = runBlocking<Unit> {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        val current = upload(storage)(coord, clubId, png)
        storage.log.clear()

        assertFailsWith<ValidationException> { upload(storage)(coord, clubId, "nope".toByteArray()) }
        assertTrue(storage.log.isEmpty())
        assertEquals(current, club.logoUrl)
    }

    @Test
    fun whenStoringTheNewOneFailsTheClubHasNoLogoRatherThanABrokenOne() = runBlocking<Unit> {
        club = club.copy(logoUrl = "/clubs/$clubId/logo/00000000-0000-0000-0000-000000000001.png")
        val storage = FakeStorage(failPut = true)
        assertFailsWith<IllegalStateException> { upload(storage)(user("coord", ClubRole.COORDINATOR), clubId, png) }
        assertNull(club.logoUrl)
    }

    @Test
    fun onlyCoordinatorsAndAdminsChangeIt() = runBlocking<Unit> {
        val storage = FakeStorage()
        assertFailsWith<ForbiddenException> { upload(storage)(user("member", ClubRole.MEMBER), clubId, png) }
        assertFailsWith<ForbiddenException> { DeleteClubLogoUseCase(clubRepository, check, transactionManager, storage)(user("member2", ClubRole.MEMBER), clubId) }
        assertTrue(storage.log.isEmpty())
    }

    @Test
    fun withoutStorageUploadsAreRefused() = runBlocking<Unit> {
        assertFailsWith<ValidationException> { upload(FakeStorage(isAvailable = false))(user("coord", ClubRole.COORDINATOR), clubId, png) }
    }

    @Test
    fun deletingRemovesTheFileAndTheLink() = runBlocking {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        upload(storage)(coord, clubId, png)

        DeleteClubLogoUseCase(clubRepository, check, transactionManager, storage)(coord, clubId)

        assertTrue(storage.files.isEmpty())
        assertNull(club.logoUrl)
    }

    @Test
    fun membersGetOnlyTheCurrentLogo() = runBlocking<Unit> {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        val old = upload(storage)(coord, clubId, png).substringAfterLast('/').removeSuffix(".png")
        val current = upload(storage)(coord, clubId, png).substringAfterLast('/').removeSuffix(".png")
        val get = GetClubLogoUseCase(clubRepository, check, transactionManager, storage)
        val member = user("member", ClubRole.MEMBER)

        assertContentEquals(storage.files.values.single(), get(member, clubId, current))
        assertFailsWith<NotFoundException> { get(member, clubId, old) }
        assertFailsWith<ForbiddenException> { get(user("outsider", null), clubId, current) }
    }

    @Test
    fun aLogoUrlThatIsntOursIsNeverTurnedIntoAStorageKey() {
        assertNull(ClubLogos.keyOf(clubId, "https://example.com/logo.png"))
        assertNull(ClubLogos.keyOf(clubId, "/clubs/$clubId/logo/../../etc.png"))
        assertNull(ClubLogos.keyOf(clubId, "/clubs/other/logo/00000000-0000-0000-0000-000000000001.png"))
    }
}
