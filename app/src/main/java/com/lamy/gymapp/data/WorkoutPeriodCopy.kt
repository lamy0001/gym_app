package com.lamy.gymapp.data

internal data class PeriodWorkoutCopy(
    val workout: WorkoutEntity,
    val exercises: List<WorkoutExerciseEntity>
)

internal fun duplicateWorkoutTemplates(
    templates: List<Pair<WorkoutEntity, List<WorkoutExerciseEntity>>>,
    newId: () -> String
): List<PeriodWorkoutCopy> = templates.mapIndexed { index, (source, exercises) ->
    val copiedWorkoutId = newId()
    PeriodWorkoutCopy(
        workout = source.copy(id = copiedWorkoutId, sortOrder = index + 1),
        exercises = exercises.map { exercise -> exercise.copy(workoutId = copiedWorkoutId) }
    )
}
