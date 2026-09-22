package com.lamy.gymapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun sessionsInPeriod(sessions: List<WorkoutSessionEntity>, periodId: String?): List<WorkoutSessionEntity> =
    sessions.filter { it.periodId == periodId }

internal fun peakLoadsBySession(sets: List<SessionSetEntity>): List<Double> =
    sets.groupBy { it.sessionId }.values.mapNotNull { sessionSets -> sessionSets.mapNotNull { it.loadKg }.maxOrNull() }

internal fun scheduledDaysThisWeek(
    sessions: List<WorkoutSessionEntity>,
    today: LocalDate,
    zoneId: ZoneId
): Int {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return sessions.mapNotNull { session ->
        session.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
    }.filter { date -> date in monday..today && date.dayOfWeek.value <= 5 }.distinct().size
}
