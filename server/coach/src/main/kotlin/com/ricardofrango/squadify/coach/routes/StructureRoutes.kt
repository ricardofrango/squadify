package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.coach.domain.CoachPlans
import com.ricardofrango.squadify.coach.domain.CoachSeasons
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.usecase.AddPlayersToAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.CreateAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.CreateOpponentUseCase
import com.ricardofrango.squadify.domain.usecase.CreatePlayersUseCase
import com.ricardofrango.squadify.domain.usecase.CreateTeamUseCase
import com.ricardofrango.squadify.domain.usecase.CreateTeamsUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteTeamUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupPlayersByClubUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetEligiblePlayersForAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentListUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.squadify.domain.usecase.GetPlayerUseCase
import com.ricardofrango.squadify.domain.usecase.GetPlayersUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamPlayersUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamsUseCase
import com.ricardofrango.squadify.domain.usecase.RemovePlayerFromAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateOpponentUseCase
import com.ricardofrango.squadify.domain.usecase.UpdatePlayerUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateTeamUseCase
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.CreateTeamRequest
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
import com.ricardofrango.squadify.model.UpdateOpponentRequest
import com.ricardofrango.squadify.model.UpdatePlayerRequest
import com.ricardofrango.squadify.model.UpdateTeamRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Age groups, teams, players and opponents: the shared use cases on the coach's policy, at the clubs
 * server's paths, with its requests and responses, so the apps call both the same way. Only the
 * season differs: creating age groups without one uses the current season (CoachSeasons). The games
 * and stats are the shared gameRoutes (:server:web).
 */
fun Route.structureRoutes() {
    authenticate("auth-jwt") {
        ageGroupRoutes()
        teamRoutes()
        playerRoutes()
        opponentRoutes()
    }
}

private fun RoutingCall.clubIdParam(): String? = request.queryParameters["clubId"]

private suspend fun RoutingCall.clubIdRequired() =
    respond(HttpStatusCode.BadRequest, ErrorResponse("clubId is required", "VAL_001"))

private fun Route.ageGroupRoutes() {
    val getAgeGroups by inject<GetAgeGroupsUseCase>()
    val createAgeGroups by inject<CreateAgeGroupsUseCase>()
    val updateAgeGroup by inject<UpdateAgeGroupUseCase>()
    val getAgeGroupDetails by inject<GetAgeGroupDetailsUseCase>()
    val getEligiblePlayers by inject<GetEligiblePlayersForAgeGroupUseCase>()
    val addPlayers by inject<AddPlayersToAgeGroupUseCase>()
    val removePlayer by inject<RemovePlayerFromAgeGroupUseCase>()
    val coachSeasons by inject<CoachSeasons>()
    val coachPlans by inject<CoachPlans>()
    val accessPolicy by inject<AccessPolicy>()
    val transactionManager by inject<TransactionManager>()

    route("/age-groups") {
        get {
            val clubId = call.clubIdParam() ?: return@get call.clubIdRequired()
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getAgeGroups(userId, clubId))
        }
        post {
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<CreateAgeGroupsRequest>()
            // Seasons are automatic: the one running today, created the first time it's needed. Only
            // once the caller may create age groups here, so no one else's space gets a season.
            val seasonId = request.seasonId ?: transactionManager.runInTransaction {
                accessPolicy.requireCreateAgeGroups(userId, request.clubId)
                coachSeasons.current(request.clubId).id
            }
            call.respond(HttpStatusCode.Created, createAgeGroups(userId, request.copy(seasonId = seasonId)))
        }
        route("/{ageGroupId}") {
            patch {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                updateAgeGroup(userId, ageGroupId, call.receive<UpdateAgeGroupRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/details") {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val details = getAgeGroupDetails(userId, ageGroupId)
                // The plan's answer for the Members tab and Invite staff: no room for more staff.
                call.respond(details.copy(staffFullOnPlan = transactionManager.runInTransaction { coachPlans.staffFullOn(details.club.id, ageGroupId) }))
            }
            route("/players") {
                // With `limit`, a page (`offset`) searched on `q`; without, all of them.
                get("/eligible") {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val params = call.request.queryParameters
                    val limit = Paging.limitOf(params["limit"])
                    if (limit == null) {
                        call.respond(getEligiblePlayers(userId, ageGroupId))
                    } else {
                        call.respond(getEligiblePlayers.page(userId, ageGroupId, params["q"], limit, Paging.offsetOf(params["offset"])))
                    }
                }
                post {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    call.respond(HttpStatusCode.Created, addPlayers(userId, ageGroupId, call.receive<AddPlayersToAgeGroupRequest>()))
                }
                delete("/{playerId}") {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val playerId = call.parameters["playerId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val userId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    removePlayer(userId, ageGroupId, playerId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun Route.teamRoutes() {
    val getTeams by inject<GetTeamsUseCase>()
    val createTeam by inject<CreateTeamUseCase>()
    val createTeams by inject<CreateTeamsUseCase>()
    val getTeamPlayers by inject<GetTeamPlayersUseCase>()
    val getTeamDetails by inject<GetTeamDetailsUseCase>()
    val updateTeam by inject<UpdateTeamUseCase>()
    val deleteTeam by inject<DeleteTeamUseCase>()

    route("/teams") {
        get {
            val clubId = call.clubIdParam() ?: return@get call.clubIdRequired()
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getTeams(userId, clubId))
        }
        post {
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, createTeam(userId, call.receive<CreateTeamRequest>()))
        }
        post("/batch") {
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, createTeams(userId, call.receive<CreateTeamsRequest>()))
        }
        route("/{teamId}") {
            get("/players") {
                val teamId = call.parameters["teamId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getTeamPlayers(userId, teamId))
            }
            get("/details") {
                val teamId = call.parameters["teamId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getTeamDetails(userId, teamId))
            }
            patch {
                val teamId = call.parameters["teamId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                updateTeam(userId, teamId, call.receive<UpdateTeamRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
            // The team and all its matches (DeleteTeamUseCase).
            delete {
                val teamId = call.parameters["teamId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteTeam(userId, teamId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun Route.playerRoutes() {
    val getPlayers by inject<GetPlayersUseCase>()
    val getAgeGroupPlayersByClub by inject<GetAgeGroupPlayersByClubUseCase>()
    val createPlayers by inject<CreatePlayersUseCase>()
    val getPlayer by inject<GetPlayerUseCase>()
    val updatePlayer by inject<UpdatePlayerUseCase>()

    route("/players") {
        get("/associations/age-group") {
            val clubId = call.clubIdParam() ?: return@get call.clubIdRequired()
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getAgeGroupPlayersByClub(userId, clubId))
        }
        get {
            val clubId = call.clubIdParam() ?: return@get call.clubIdRequired()
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val params = call.request.queryParameters
            // With `limit`: one page, filtered here. Without: the whole list (or a team's pool).
            val limit = Paging.limitOf(params["limit"])
            if (limit != null) {
                call.respond(
                    getPlayers.page(
                        userId, clubId,
                        seasonId = params["seasonId"],
                        ageGroupId = params["ageGroupId"],
                        search = params["q"],
                        limit = limit,
                        offset = Paging.offsetOf(params["offset"])
                    )
                )
            } else {
                call.respond(getPlayers(userId, clubId, params["teamId"]))
            }
        }
        post {
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, createPlayers(userId, call.receive<CreatePlayersRequest>()))
        }
        route("/{playerId}") {
            get {
                val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getPlayer(userId, playerId))
            }
            patch {
                val playerId = call.parameters["playerId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                updatePlayer(userId, playerId, call.receive<UpdatePlayerRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun Route.opponentRoutes() {
    val getOpponents by inject<GetOpponentsUseCase>()
    val getOpponentList by inject<GetOpponentListUseCase>()
    val createOpponent by inject<CreateOpponentUseCase>()
    val getOpponentDetails by inject<GetOpponentDetailsUseCase>()
    val updateOpponent by inject<UpdateOpponentUseCase>()

    route("/opponents") {
        get {
            val clubId = call.clubIdParam() ?: return@get call.clubIdRequired()
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getOpponents(userId, clubId, call.request.queryParameters["ageGroupId"]))
        }
        // The Opponents screen. With `limit`, a page (`offset`) searched on `q`; without, the whole list.
        get("/list") {
            val params = call.request.queryParameters
            val clubId = call.clubIdParam() ?: return@get call.clubIdRequired()
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(
                getOpponentList(
                    userId, clubId, params["ageGroupId"],
                    search = params["q"],
                    limit = Paging.limitOf(params["limit"]),
                    offset = Paging.offsetOf(params["offset"])
                )
            )
        }
        post {
            val clubId = call.clubIdParam() ?: return@post call.clubIdRequired()
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, createOpponent(userId, clubId, call.receive<CreateOpponentRequest>()))
        }
        get("/{opponentId}") {
            val opponentId = call.parameters["opponentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getOpponentDetails(userId, opponentId))
        }
        patch("/{opponentId}") {
            val opponentId = call.parameters["opponentId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
            val userId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            updateOpponent(userId, opponentId, call.receive<UpdateOpponentRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
