package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.ImageStorage
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.CreateOpponentRequest
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.UpdateOpponentRequest
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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

/** An opponent's logo: the club logo's rules (ClubLogoUseCasesTest), behind Edit opponent's gate. */
class OpponentLogoUseCasesTest {

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
    private val opponentId = "opp-1"
    private val ag1 = "ag-1"
    private var opponent = Opponent(id = opponentId, clubId = clubId, name = "Rivals FC")

    private val opponentRepository = mockk<OpponentRepository>(relaxed = true)
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
        coEvery { opponentRepository.getOpponentById(opponentId) } answers { opponent }
        coEvery { opponentRepository.updateLogoUrl(opponentId, any()) } answers { opponent = opponent.copy(logoUrl = secondArg()); true }
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(any(), any()) } returns null
    }

    private fun user(id: String, role: ClubRole?, ageGroupRole: AgeGroupRole? = null): String {
        coEvery { userRepository.findById(id) } returns User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.getUserRoleInClub(id, clubId) } returns role
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(id, ag1) } returns ageGroupRole
        return id
    }

    private val png: ByteArray = ByteArrayOutputStream().use { ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", it); it.toByteArray() }

    private fun upload(storage: ImageStorage) = UploadOpponentLogoUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(check), transactionManager, storage)
    private fun delete(storage: ImageStorage) = DeleteOpponentLogoUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(check), transactionManager, storage)

    @Test
    fun replacingALogoDeletesTheOldFileFirstThenStoresTheNewOneUnderANewId() = runBlocking {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)

        val first = upload(storage)(coord, opponentId, null, png)
        assertTrue(first.startsWith("/opponents/$opponentId/logo/") && first.endsWith(".png"))
        assertEquals(first, opponent.logoUrl)

        storage.log.clear()
        val second = upload(storage)(coord, opponentId, null, png)

        assertNotEquals(first, second)
        assertEquals(listOf("delete ${first.removePrefix("/")}", "put ${second.removePrefix("/")}"), storage.log)
        assertEquals(setOf(second.removePrefix("/")), storage.files.keys)
        assertEquals(second, opponent.logoUrl)
    }

    @Test
    fun theAgeGroupsCoachCanSetIt() = runBlocking {
        val coach = user("coach", ClubRole.MEMBER, AgeGroupRole.COACH)
        val url = upload(FakeStorage())(coach, opponentId, ag1, png)
        assertEquals(url, opponent.logoUrl)
    }

    @Test
    fun aMemberWhoCantEditTheOpponentCantChangeIt() = runBlocking<Unit> {
        val storage = FakeStorage()
        val member = user("member", ClubRole.MEMBER)
        assertFailsWith<ForbiddenException> { upload(storage)(member, opponentId, null, png) }
        assertFailsWith<ForbiddenException> { upload(storage)(member, opponentId, ag1, png) }
        assertFailsWith<ForbiddenException> { delete(storage)(member, opponentId, null) }
        assertTrue(storage.log.isEmpty())
    }

    @Test
    fun anAgeGroupOfAnotherClubIsRefused() = runBlocking<Unit> {
        coEvery { ageGroupRepository.getAgeGroupById("ag-other") } returns AgeGroup("ag-other", "club-2", "U12", "s-2", 2013, 2013)
        val coach = user("coach", ClubRole.MEMBER, AgeGroupRole.COACH)
        assertFailsWith<ForbiddenException> { upload(FakeStorage())(coach, opponentId, "ag-other", png) }
    }

    @Test
    fun aBadImageLeavesTheCurrentLogoAlone() = runBlocking<Unit> {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        val current = upload(storage)(coord, opponentId, null, png)
        storage.log.clear()

        assertFailsWith<ValidationException> { upload(storage)(coord, opponentId, null, "nope".toByteArray()) }
        assertTrue(storage.log.isEmpty())
        assertEquals(current, opponent.logoUrl)
    }

    @Test
    fun whenStoringTheNewOneFailsTheOpponentHasNoLogoRatherThanABrokenOne() = runBlocking<Unit> {
        opponent = opponent.copy(logoUrl = "/opponents/$opponentId/logo/00000000-0000-0000-0000-000000000001.png")
        assertFailsWith<IllegalStateException> { upload(FakeStorage(failPut = true))(user("coord", ClubRole.COORDINATOR), opponentId, null, png) }
        assertNull(opponent.logoUrl)
    }

    @Test
    fun withoutStorageUploadsAreRefused() = runBlocking<Unit> {
        assertFailsWith<ValidationException> { upload(FakeStorage(isAvailable = false))(user("coord", ClubRole.COORDINATOR), opponentId, null, png) }
    }

    @Test
    fun deletingRemovesTheFileAndTheLink() = runBlocking {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        upload(storage)(coord, opponentId, null, png)

        delete(storage)(coord, opponentId, null)

        assertTrue(storage.files.isEmpty())
        assertNull(opponent.logoUrl)
    }

    @Test
    fun membersGetOnlyTheCurrentLogo() = runBlocking<Unit> {
        val storage = FakeStorage()
        val coord = user("coord", ClubRole.COORDINATOR)
        val old = upload(storage)(coord, opponentId, null, png).substringAfterLast('/').removeSuffix(".png")
        val current = upload(storage)(coord, opponentId, null, png).substringAfterLast('/').removeSuffix(".png")
        val get = GetOpponentLogoUseCase(opponentRepository, ClubAccessPolicy(check), transactionManager, storage)
        val member = user("member", ClubRole.MEMBER)

        assertContentEquals(storage.files.values.single(), get(member, opponentId, current))
        assertFailsWith<NotFoundException> { get(member, opponentId, old) }
        assertFailsWith<ForbiddenException> { get(user("outsider", null), opponentId, current) }
    }

    @Test
    fun aLogoUrlSentWithTheFormIsIgnored() = runBlocking {
        val coord = user("coord", ClubRole.COORDINATOR)
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns emptyList()
        val created = slot<Opponent>()
        coEvery { opponentRepository.createOpponent(capture(created)) } answers { created.captured }
        CreateOpponentUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(check), transactionManager)(
            coord, clubId, CreateOpponentRequest(name = "New FC", logoUrl = "https://example.com/x.png")
        )
        assertNull(created.captured.logoUrl)

        opponent = opponent.copy(logoUrl = "/opponents/$opponentId/logo/00000000-0000-0000-0000-000000000001.png")
        val updated = slot<Opponent>()
        coEvery { opponentRepository.updateOpponent(capture(updated)) } returns true
        UpdateOpponentUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(check), transactionManager)(
            coord, opponentId, UpdateOpponentRequest(logoUrl = "https://example.com/x.png")
        )
        assertEquals(opponent.logoUrl, updated.captured.logoUrl)
    }

    @Test
    fun aLogoUrlThatIsntOursIsNeverTurnedIntoAStorageKey() {
        assertNull(OpponentLogos.keyOf(opponentId, "https://example.com/logo.png"))
        assertNull(OpponentLogos.keyOf(opponentId, "/opponents/$opponentId/logo/../../etc.png"))
        assertNull(OpponentLogos.keyOf(opponentId, "/clubs/$opponentId/logo/00000000-0000-0000-0000-000000000001.png"))
    }
}
