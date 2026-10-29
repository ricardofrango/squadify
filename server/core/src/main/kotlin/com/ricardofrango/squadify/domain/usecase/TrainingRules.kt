package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.model.TrainingSession
import kotlinx.datetime.LocalDateTime

/**
 * Whether a session's ratings are open: it has ended (in the server's time zone, the club's) and isn't
 * cancelled. Here, not with the clubs server's training use cases, because full stats counts the ended
 * sessions' ratings.
 */
fun TrainingSession.ratingsOpen(now: LocalDateTime): Boolean =
    !cancelled && (date < now.date || (date == now.date && endTime <= now.time))
