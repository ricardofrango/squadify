package com.ricardofrango.footballmanager.security

import com.ricardofrango.footballmanager.model.LiveMatchUpdate
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object MatchSessionManager {
    // Map of matchId to list of sessions
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<DefaultWebSocketServerSession>>()

    fun addSession(matchId: String, session: DefaultWebSocketServerSession) {
        val matchSessions = sessions.getOrPut(matchId) { CopyOnWriteArrayList() }
        matchSessions.add(session)
    }

    fun removeSession(matchId: String, session: DefaultWebSocketServerSession) {
        sessions[matchId]?.remove(session)
        if (sessions[matchId]?.isEmpty() == true) {
            sessions.remove(matchId)
        }
    }

    suspend fun broadcastUpdate(matchId: String, update: LiveMatchUpdate) {
        sessions[matchId]?.forEach { session ->
            try {
                session.sendSerialized(update)
            } catch (e: Exception) {
                // Remove failed sessions (could be closed)
                removeSession(matchId, session)
            }
        }
    }
}
