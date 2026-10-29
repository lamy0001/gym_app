package com.lamy.gymapp.data

/** Same-session edits adjust the baseline; only a later execution can fulfil the goal. */
internal fun progressionAfterLoadChange(goal: ExerciseProgressionEntity, sessionId: String?, load: Double): ExerciseProgressionEntity? =
    if (sessionId == goal.sourceSessionId) goal.copy(referenceLoadKg = load)
    else if (goal.referenceLoadKg != null && load > goal.referenceLoadKg) null
    else goal

internal fun toggleExerciseSets(sessionId: String, exerciseId: String, count: Int, saved: List<SessionSetEntity>, loads: List<Double?>): List<SessionSetEntity> {
    val complete = !allSetsCompleted(saved, count)
    return (1..count).map { number ->
        val previous = saved.firstOrNull { it.setIndex == number }
            ?: SessionSetEntity("$sessionId-$exerciseId-$number", sessionId, exerciseId, number)
        previous.copy(loadKg = previous.loadKg ?: loads.getOrNull(number - 1), completed = complete)
    }
}
