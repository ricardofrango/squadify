package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.ChangeTrainingSessionUseCase
import com.ricardofrango.squadify.domain.usecase.CreateTrainingSessionsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.ricardofrango.squadify.domain.usecase.SaveTrainingSessionUseCase
import com.ricardofrango.squadify.domain.usecase.TrainingExercisesUseCase
import com.ricardofrango.squadify.model.SaveExerciseRequest
import com.ricardofrango.squadify.model.SetSessionExercisesRequest
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/** Training sessions (design 39): an age group's sessions, bulk creation, and one session's changes. */
fun Route.trainingRoutes() {
    val getSessions by inject<GetTrainingSessionsUseCase>()
    val createSessions by inject<CreateTrainingSessionsUseCase>()
    val saveSession by inject<SaveTrainingSessionUseCase>()
    val changeSession by inject<ChangeTrainingSessionUseCase>()
    val getSession by inject<GetTrainingSessionUseCase>()
    val exercises by inject<TrainingExercisesUseCase>()

    authenticate("auth-jwt") {
        route("/age-groups/{ageGroupId}/training-sessions") {
            get {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getSessions(userId, ageGroupId))
            }
            post {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.Created, saveSession.add(userId, ageGroupId, call.receive<SaveTrainingSessionRequest>()))
            }
            // What a bulk creation would do, without doing it.
            post("/preview") {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(createSessions.preview(userId, ageGroupId, call.receive<CreateTrainingSessionsRequest>()))
            }
            post("/bulk") {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.Created, createSessions.create(userId, ageGroupId, call.receive<CreateTrainingSessionsRequest>()))
            }
        }

        // The age group's exercise library (design 39h).
        route("/age-groups/{ageGroupId}/exercises") {
            get {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(exercises.library(userId, ageGroupId))
            }
            post {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.Created, exercises.create(userId, ageGroupId, call.receive<SaveExerciseRequest>()))
            }
        }

        patch("/exercises/{exerciseId}") {
            val exerciseId = call.parameters["exerciseId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
            val userId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            call.respond(exercises.update(userId, exerciseId, call.receive<SaveExerciseRequest>()))
        }

        route("/training-sessions/{sessionId}") {
            // The session's exercises, in order: adds, removes and reorders in one go.
            put("/exercises") {
                val sessionId = call.parameters["sessionId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                call.respond(exercises.setForSession(userId, sessionId, call.receive<SetSessionExercisesRequest>().exerciseIds))
            }
            get {
                val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getSession(userId, sessionId))
            }
            patch {
                val sessionId = call.parameters["sessionId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                call.respond(saveSession.update(userId, sessionId, call.receive<SaveTrainingSessionRequest>()))
            }
            delete {
                val sessionId = call.parameters["sessionId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                changeSession.delete(userId, sessionId)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/cancel") {
                val sessionId = call.parameters["sessionId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                changeSession.cancel(userId, sessionId)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/restore") {
                val sessionId = call.parameters["sessionId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                changeSession.restore(userId, sessionId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
