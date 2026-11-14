package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.data.database.AgeGroupStaff
import com.ricardofrango.squadify.data.database.AgeGroups
import com.ricardofrango.squadify.data.database.ClubMembers
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.util.toUuid
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
