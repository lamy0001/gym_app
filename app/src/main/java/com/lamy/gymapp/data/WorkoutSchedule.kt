package com.lamy.gymapp.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal val defaultTrainingDays = setOf(1, 2, 3, 4, 5)

internal fun trainingDays(value: String?): Set<Int> =
    if (value == null) defaultTrainingDays else value.split(',').mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 } }.toSet()

internal fun reminderTime(value: String?): LocalTime =
    runCatching { LocalTime.parse(value ?: "07:00") }.getOrDefault(LocalTime.of(7, 0))

/** On rest days, show the next scheduled workout. Extra workouts remain swipeable. */
internal fun workoutIndexForDate(date: LocalDate, days: Set<Int>, workoutCount: Int): Int {
    if (workoutCount <= 0 || days.isEmpty()) return 0
    val orderedDays = days.sorted()
    val nextDay = orderedDays.firstOrNull { it >= date.dayOfWeek.value } ?: orderedDays.first()
    return orderedDays.indexOf(nextDay) % workoutCount
}

internal fun nextReminder(now: ZonedDateTime, time: LocalTime, days: Set<Int>): ZonedDateTime? =
    (0L..7L).asSequence().map { offset -> now.toLocalDate().plusDays(offset).atTime(time).atZone(now.zone) }
        .firstOrNull { it.dayOfWeek.value in days && it.isAfter(now) }

internal fun parseLoad(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

internal fun validLoadInput(value: String): Boolean = value.matches(Regex("\\d*([.,]\\d*)?"))

internal fun allSetsCompleted(sets: List<SessionSetEntity>, setCount: Int): Boolean =
    setCount > 0 && (1..setCount).all { index -> sets.any { it.setIndex == index && it.completed } }
