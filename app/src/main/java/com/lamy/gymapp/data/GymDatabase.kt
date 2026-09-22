package com.lamy.gymapp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val sortOrder: Int
)

@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["workoutId", "exerciseId"]
)
data class WorkoutExerciseEntity(
    val workoutId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val restSeconds: Int,
    val plannedReps: String
)

@Entity(
    tableName = "exercise_load_profiles",
    primaryKeys = ["exerciseId", "setIndex"]
)
data class ExerciseLoadProfileEntity(
    val exerciseId: String,
    val setIndex: Int,
    val lastUsedLoadKg: Double
)

data class WorkoutExerciseRow(
    val exerciseId: String,
    val name: String,
    val muscleGroup: String,
    val restSeconds: Int,
    val plannedReps: String
)

@Dao
interface GymDao {
    @Query("SELECT * FROM workouts ORDER BY sortOrder")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun workoutCount(): Int

    @Query("SELECT * FROM workout_exercises we INNER JOIN exercises e ON e.id = we.exerciseId WHERE we.workoutId = :workoutId ORDER BY we.sortOrder")
    fun observeWorkoutExercises(workoutId: String): Flow<List<WorkoutExerciseRow>>

    @Query("SELECT * FROM exercise_load_profiles WHERE exerciseId = :exerciseId ORDER BY setIndex")
    fun observeLoadProfile(exerciseId: String): Flow<List<ExerciseLoadProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(items: List<WorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLoadProfile(items: List<ExerciseLoadProfileEntity>)
}

@Database(
    entities = [ExerciseEntity::class, WorkoutEntity::class, WorkoutExerciseEntity::class, ExerciseLoadProfileEntity::class],
    version = 1,
    exportSchema = true
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao

    companion object {
        fun create(context: Context): GymDatabase = Room.databaseBuilder(
            context,
            GymDatabase::class.java,
            "gym_app.db"
        ).build()
    }
}

suspend fun GymDao.seedIfEmpty() {
    if (workoutCount() > 0) return

    val exercises = listOf(
        ExerciseEntity("ankle-mobility", "Mobilidade de tornozelo", "Preparação"),
        ExerciseEntity("scapula-position", "Posicionamento das escápulas", "Preparação"),
        ExerciseEntity("bosu-crunch", "Abdominal infra no bosu", "Abdômen"),
        ExerciseEntity("supinated-pulldown", "Puxada supinada", "Costas"),
        ExerciseEntity("pronated-row", "Remada máquina pegada pronada", "Costas"),
        ExerciseEntity("convergent-row", "Serrote convergente", "Costas"),
        ExerciseEntity("face-pull", "Face pull", "Ombros"),
        ExerciseEntity("dumbbell-bench", "Supino reto com halteres", "Peito"),
        ExerciseEntity("incline-machine-press", "Supino inclinado máquina", "Peito"),
        ExerciseEntity("machine-shoulder-press", "Desenvolvimento máquina", "Ombros")
    )
    val workouts = listOf(
        WorkoutEntity("upper-1", "Upper", "Costas · Peito · Ombros · Braços", 1),
        WorkoutEntity("lower-1", "Lower", "Quadríceps · Posterior · Glúteos", 2),
        WorkoutEntity("cardio-1", "Cardio", "Caminhada · 6 km · 60 min", 3),
        WorkoutEntity("upper-2", "Upper 2", "Costas · Peito · Ombros · Braços", 4),
        WorkoutEntity("lower-2", "Lower 2", "Quadríceps · Posterior · Glúteos", 5)
    )
    val links = listOf(
        WorkoutExerciseEntity("upper-1", "ankle-mobility", 1, 30, "10–12"),
        WorkoutExerciseEntity("upper-1", "scapula-position", 2, 30, "—"),
        WorkoutExerciseEntity("upper-1", "bosu-crunch", 3, 60, "10–12"),
        WorkoutExerciseEntity("upper-1", "supinated-pulldown", 4, 90, "10–12"),
        WorkoutExerciseEntity("upper-1", "pronated-row", 5, 90, "10–12"),
        WorkoutExerciseEntity("upper-1", "convergent-row", 6, 90, "10–12"),
        WorkoutExerciseEntity("upper-1", "face-pull", 7, 60, "10–12"),
        WorkoutExerciseEntity("upper-1", "dumbbell-bench", 8, 90, "10–12"),
        WorkoutExerciseEntity("upper-1", "incline-machine-press", 9, 90, "10–12"),
        WorkoutExerciseEntity("upper-1", "machine-shoulder-press", 10, 90, "10–12"),
        WorkoutExerciseEntity("upper-2", "supinated-pulldown", 1, 90, "10–12"),
        WorkoutExerciseEntity("upper-2", "pronated-row", 2, 90, "10–12"),
        WorkoutExerciseEntity("upper-2", "machine-shoulder-press", 3, 90, "10–12")
    )
    insertExercises(exercises)
    insertWorkouts(workouts)
    insertWorkoutExercises(links)
}
