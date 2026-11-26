package com.sportssoftw.squadcall.coach.data

import com.sportssoftw.squadcall.data.database.AgeGroupStaff
import com.sportssoftw.squadcall.data.database.AgeGroups
import com.sportssoftw.squadcall.data.database.ClubMembers
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.util.toUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll

/** A staff member leaving a space (REQUIREMENTS §5c, "Leave space"), on the coach server only. */
interface SpaceMembershipRepository {
    /**
     * Takes [userId] out of [spaceId]: their membership and their staff access to each of its age groups.
     * The owner can't leave their own space (ownership transfer comes later). Call inside a transaction.
     */
    suspend fun leave(userId: String, spaceId: String)
}

class ExposedSpaceMembershipRepository : SpaceMembershipRepository {
    override suspend fun leave(userId: String, spaceId: String) {
        val user = userId.toUuid()
        val space = spaceId.toUuid()
        val role = ClubMembers.selectAll().where { (ClubMembers.clubId eq space) and (ClubMembers.userId eq user) }
            .singleOrNull()?.get(ClubMembers.role) ?: throw ForbiddenException("You're not in this space")
        if (role == ClubRole.CLUB_ADMIN) throw ForbiddenException("The owner can't leave their space")
        val ageGroupIds = AgeGroups.selectAll().where { AgeGroups.clubId eq space }.map { it[AgeGroups.id] }
        AgeGroupStaff.deleteWhere { (AgeGroupStaff.userId eq user) and (AgeGroupStaff.ageGroupId inList ageGroupIds) }
        ClubMembers.deleteWhere { (ClubMembers.clubId eq space) and (ClubMembers.userId eq user) }
    }
}
