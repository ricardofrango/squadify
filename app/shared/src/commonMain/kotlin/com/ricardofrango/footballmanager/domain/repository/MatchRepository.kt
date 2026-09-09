package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.CreateMatchRequest
import com.ricardofrango.footballmanager.model.Match

interface MatchRepository {
    suspend fun createMatch(request: CreateMatchRequest): Match
}
