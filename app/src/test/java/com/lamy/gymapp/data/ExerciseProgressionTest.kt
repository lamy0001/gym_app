package com.lamy.gymapp.data

import org.junit.Assert.*
import org.junit.Test

class ExerciseProgressionTest {
    @Test fun bulkActionIsReversibleAndPreservesLoadsAndPlus() {
        val saved = listOf(SessionSetEntity("s-e-1", "s", "e", 1, reps = 12, loadKg = 8.0, completed = true, increaseMarked = true))
        val checked = toggleExerciseSets("s", "e", 2, saved, listOf(99.0, 6.0))
        assertTrue(allSetsCompleted(checked, 2))
        assertEquals(8.0, checked[0].loadKg!!, 0.0)
        assertEquals(12, checked[0].reps)
        val unchecked = toggleExerciseSets("s", "e", 2, checked, emptyList())
        assertTrue(unchecked.none { it.completed })
        assertTrue(unchecked[0].increaseMarked)
        assertEquals(checked.map { it.loadKg }, unchecked.map { it.loadKg })
        assertEquals(checked, toggleExerciseSets("s", "e", 2, unchecked, emptyList()))
    }

    @Test fun singleSeriesAndEmptyExerciseAreHandled() {
        val checked = toggleExerciseSets("s", "e", 1, emptyList(), listOf(7.0))
        assertTrue(checked.single().completed)
        assertFalse(toggleExerciseSets("s", "e", 1, checked, emptyList()).single().completed)
        assertTrue(toggleExerciseSets("s", "e", 0, emptyList(), emptyList()).isEmpty())
    }

    @Test fun onlyHigherLoadInLaterExecutionFulfilsProgression() {
        val goal = ExerciseProgressionEntity("e", 1, "old", 8.0)
        assertEquals(goal, progressionAfterLoadChange(goal, "new", 8.0))
        assertEquals(goal, progressionAfterLoadChange(goal, "new", 6.0))
        assertNull(progressionAfterLoadChange(goal, "new", 9.0))
        assertNull(progressionAfterLoadChange(goal, null, 9.0))
        assertEquals(goal.copy(referenceLoadKg = 10.0), progressionAfterLoadChange(goal, "old", 10.0))
    }

    @Test fun unknownBaselineDoesNotInventAnIncrease() {
        val goal = ExerciseProgressionEntity("e", 2, "old", null)
        assertEquals(goal, progressionAfterLoadChange(goal, "new", 10.0))
    }
}
