package com.lamy.gymapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkoutPeriodCopyTest {
    @Test
    fun copiesWorkoutAndExerciseSetupToIndependentIds() {
        val workout = WorkoutEntity("old-workout", "Upper", "Peito · Costas", 8)
        val exercise = WorkoutExerciseEntity("old-workout", "supinated-pulldown", 3, 90, "10–12", 4, "20,22.5,22.5,25")
        val copies = duplicateWorkoutTemplates(listOf(workout to listOf(exercise))) { "new-workout" }

        assertEquals(1, copies.size)
        assertNotEquals(workout.id, copies.single().workout.id)
        assertEquals("Upper", copies.single().workout.title)
        assertEquals("Peito · Costas", copies.single().workout.subtitle)
        assertEquals(1, copies.single().workout.sortOrder)
        assertEquals("new-workout", copies.single().exercises.single().workoutId)
        assertEquals(exercise.exerciseId, copies.single().exercises.single().exerciseId)
        assertEquals(exercise.restSeconds, copies.single().exercises.single().restSeconds)
        assertEquals(exercise.plannedReps, copies.single().exercises.single().plannedReps)
        assertEquals(exercise.setCount, copies.single().exercises.single().setCount)
        assertEquals(exercise.plannedLoadsCsv, copies.single().exercises.single().plannedLoadsCsv)
    }

    @Test
    fun assignsSequentialWorkoutNumbersInsideNewPeriod() {
        val templates = listOf(
            WorkoutEntity("a", "Upper", "A", 4) to emptyList<WorkoutExerciseEntity>(),
            WorkoutEntity("b", "Lower", "B", 9) to emptyList<WorkoutExerciseEntity>()
        )
        val ids = listOf("copy-a", "copy-b").iterator()
        val copies = duplicateWorkoutTemplates(templates) { ids.next() }

        assertEquals(listOf(1, 2), copies.map { it.workout.sortOrder })
        assertEquals(listOf("copy-a", "copy-b"), copies.map { it.workout.id })
    }

    @Test
    fun historyMetricsOnlyUseTheSelectedPeriodAndSkipWeekendDays() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val today = LocalDate.of(2026, 9, 24) // Thursday
        fun timestamp(day: LocalDate) = day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity("a1", "upper-a", timestamp(today.minusDays(1)), timestamp(today.minusDays(1)), true, "period-a"),
            WorkoutSessionEntity("a2", "lower-a", timestamp(today.minusDays(2)), timestamp(today.minusDays(2)), true, "period-a"),
            WorkoutSessionEntity("a3", "cardio-a", timestamp(today.minusDays(3)), timestamp(today.minusDays(3)), true, "period-a"),
            WorkoutSessionEntity("a4", "extra-a", timestamp(today.minusDays(5)), timestamp(today.minusDays(5)), true, "period-a"),
            WorkoutSessionEntity("b1", "upper-b", timestamp(today), timestamp(today), true, "period-b"),
            WorkoutSessionEntity("a5", "unfinished", timestamp(today), timestamp(today), false, "period-a"),
            WorkoutSessionEntity("a6", "not-finished", timestamp(today), null, true, "period-a")
        )

        val selected = sessionsInPeriod(sessions, "period-a")
        assertEquals(4, selected.size)
        assertEquals(3, scheduledDaysThisWeek(selected, today, zone))
        assertEquals(1, sessionsInPeriod(sessions, "period-b").size)
        assertEquals(1, scheduledDaysThisWeek(sessionsInPeriod(sessions, "period-b"), today, zone))
    }

    @Test
    fun frequencyCountsDistinctWeekdaysOnlyFromFinishedSessions() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val monday = LocalDate.of(2026, 9, 21)
        fun timestamp(day: LocalDate) = day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity("m1", "upper", timestamp(monday), timestamp(monday), true, "period-a"),
            WorkoutSessionEntity("m2", "lower", timestamp(monday), timestamp(monday), true, "period-a"),
            WorkoutSessionEntity("sat", "extra", timestamp(monday.plusDays(5)), timestamp(monday.plusDays(5)), true, "period-a"),
            WorkoutSessionEntity("open", "open", timestamp(monday.plusDays(1)), null, true, "period-a")
        )

        assertEquals(1, scheduledDaysThisWeek(sessionsInPeriod(sessions, "period-a"), monday.plusDays(6), zone))
        assertEquals(0, scheduledDaysThisWeek(emptyList(), monday.plusDays(6), zone))
    }

    @Test
    fun exerciseChartUsesOnePeakLoadBarPerWorkoutSession() {
        val sets = listOf(
            SessionSetEntity("a1", "session-a", "row", 1, loadKg = 20.0, completed = true),
            SessionSetEntity("a2", "session-a", "row", 2, loadKg = 22.5, completed = true),
            SessionSetEntity("a3", "session-a", "row", 3, loadKg = 25.0, completed = true),
            SessionSetEntity("b1", "session-b", "row", 1, loadKg = 25.0, completed = true),
            SessionSetEntity("b2", "session-b", "row", 2, loadKg = 30.0, completed = true)
        )

        assertEquals(listOf(25.0, 30.0), peakLoadsBySession(sets))
    }
}
