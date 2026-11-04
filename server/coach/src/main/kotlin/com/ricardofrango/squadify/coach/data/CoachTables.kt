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
 * Free (PlanLimits.FREE). Goes with its space.
 */
object SpacePlans : Table("space_plans") {
    val clubId = uuid("club_id").references(Clubs.id, onDelete = ReferenceOption.CASCADE)
    // The plan's id (PlanLimits), text so a new plan needs no migration.
    val plan = varchar("plan", 20)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(clubId)
}

/**
 * The plan a space's owner asked for (REQUIREMENTS §5c), until a platform admin sets it, which deletes
 * the row; one a space, the latest. Goes with its space and with the one who asked.
 */
object PlanRequests : Table("plan_requests") {
    val clubId = uuid("club_id").references(Clubs.id, onDelete = ReferenceOption.CASCADE)
    val plan = varchar("plan", 20)
    val requestedBy = uuid("requested_by").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val requestedAt = timestamp("requested_at")

    override val primaryKey = PrimaryKey(clubId)
}

/**
 * A subscription bought in a store (REQUIREMENTS §5c, payments), as the store last told the server: one
 * row a purchase token. Which space it pays for, the plan of its product, whether it gives the plan now
 * ([entitled]: active, or cancelled but paid until [expiresAt]) and the lower plan it goes down to at its
 * renewal ([pendingPlan]). Goes with its space, and with its buyer.
 */
object StoreSubscriptions : Table("store_subscriptions") {
    val purchaseToken = varchar("purchase_token", 512)
    val clubId = uuid("club_id").references(Clubs.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    // "GOOGLE" (Apple next).
    val store = varchar("store", 10)
    val productId = varchar("product_id", 100)
    // Google's base plan ("monthly", "yearly").
    val basePlanId = varchar("base_plan_id", 100).nullable()
    val plan = varchar("plan", 20)
    val entitled = bool("entitled")
    val expiresAt = timestamp("expires_at").nullable()
    val pendingPlan = varchar("pending_plan", 20).nullable()
    // A store's test purchase (nothing charged): only accepted off production.
    val test = bool("test")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(purchaseToken)
}

/** The coach database's own tables, beyond the shared ones (`sharedTables`). */
val coachOnlyTables: List<Table> = listOf(StaffInvites, SpacePlans, PlanRequests, StoreSubscriptions)
