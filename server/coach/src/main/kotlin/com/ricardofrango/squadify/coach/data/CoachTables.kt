package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.data.database.AgeGroups
import com.ricardofrango.squadify.data.database.Clubs
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.data.database.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * A code the owner shares to bring someone onto an age group as staff (TECHNICAL_DECISIONS §72).
 * Single-use: [acceptedBy] is set once it's used. Goes with its age group.
 */
object StaffInvites : Table("staff_invites") {
    val id = uuid("id")
    // Stored without the dash, upper case: "K7Q4MZ".
    val code = varchar("code", 16).uniqueIndex()
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id, onDelete = ReferenceOption.CASCADE)
    val createdBy = uuid("created_by").references(Users.id)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val acceptedBy = uuid("accepted_by").references(Users.id).nullable()
    val acceptedAt = timestamp("accepted_at").nullable()
    // The address the code was emailed to, if it was (V3).
    val sentTo = varchar("sent_to", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * A space's plan (REQUIREMENTS §5c), set by a platform admin until payments. A space with no row is on
 * [SpacePlan.FREE]. Goes with its space.
 */
object SpacePlans : Table("space_plans") {
    val clubId = uuid("club_id").references(Clubs.id, onDelete = ReferenceOption.CASCADE)
    val plan = enumerationByName("plan", 20, SpacePlan::class)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(clubId)
}

/** The coach database's own tables, beyond the shared ones (`sharedTables`). */
val coachOnlyTables: List<Table> = listOf(StaffInvites, SpacePlans)
