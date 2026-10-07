package com.lamy.gymapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class WorkoutScheduleTest {
    @Test fun positionsFiveWorkoutsByWeekdayAndShowsNextOnRestDays() {
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(listOf(0, 1, 2, 3, 4, 0, 0), (0L..6L).map { workoutIndexForDate(monday.plusDays(it), defaultTrainingDays, 5) })
        assertEquals(1, workoutIndexForDate(monday.plusDays(1), setOf(1, 3, 5), 3))
        assertEquals(0, workoutIndexForDate(monday, emptySet(), 0))
    }

    @Test fun reminderSkipsUnscheduledDaysAndPassedTimes() {
        val friday = ZonedDateTime.parse("2026-10-09T08:00:00-03:00[America/Sao_Paulo]")
        assertEquals(LocalDate.of(2026, 10, 12), nextReminder(friday, LocalTime.of(7, 0), defaultTrainingDays)?.toLocalDate())
        assertEquals(friday.toLocalDate(), nextReminder(friday, LocalTime.of(9, 0), defaultTrainingDays)?.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 10), nextReminder(friday, LocalTime.of(7, 0), setOf(6))?.toLocalDate())
        assertNull(nextReminder(friday, LocalTime.of(7, 0), emptySet()))
        assertEquals(emptySet<Int>(), trainingDays(""))
        assertEquals(defaultTrainingDays, trainingDays(null))
    }

    @Test fun loadAcceptsNumericDecimalsButRejectsUnitsAndInvalidValues() {
        assertEquals(12.5, parseLoad("12,5")!!, 0.0)
        assertEquals(0.0, parseLoad("0")!!, 0.0)
        assertNull(parseLoad("12 kg"))
        assertNull(parseLoad("-1"))
        assertNull(parseLoad("NaN"))
        assertTrue(validLoadInput("12,"))
        assertTrue(validLoadInput(""))
        assertFalse(validLoadInput("1,2,3"))
        assertFalse(validLoadInput("12kg"))
    }

    @Test fun completionRequiresEveryPrescribedSeries() {
        val a = SessionSetEntity("a", "session", "exercise", 1, completed = true)
        assertFalse(allSetsCompleted(listOf(a, a), 2))
        assertTrue(allSetsCompleted(listOf(a), 1))
        assertFalse(allSetsCompleted(listOf(a), 0))
        assertTrue(allSetsCompleted(listOf(a, a.copy(id = "b", setIndex = 2)), 2))
        assertFalse(allSetsCompleted(listOf(a, a.copy(id = "b", setIndex = 2, completed = false)), 2))
    }
}
