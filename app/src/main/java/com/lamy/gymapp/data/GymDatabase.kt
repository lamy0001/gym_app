package com.lamy.gymapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "exercises")
data class ExerciseEntity(@PrimaryKey val id: String, val name: String, val muscleGroup: String)

@Entity(tableName = "workouts")
data class WorkoutEntity(@PrimaryKey val id: String, val title: String, val subtitle: String, val sortOrder: Int)

@Entity(tableName = "workout_exercises", primaryKeys = ["workoutId", "exerciseId"])
data class WorkoutExerciseEntity(val workoutId: String, val exerciseId: String, val sortOrder: Int, val restSeconds: Int, val plannedReps: String, val setCount: Int = 3, val plannedLoadsCsv: String = "")

@Entity(tableName = "exercise_load_profiles", primaryKeys = ["exerciseId", "setIndex"])
data class ExerciseLoadProfileEntity(val exerciseId: String, val setIndex: Int, val lastUsedLoadKg: Double)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(@PrimaryKey val id: String, val workoutId: String, val startedAt: Long, val finishedAt: Long? = null, val completed: Boolean = false)

@Entity(tableName = "session_sets")
data class SessionSetEntity(@PrimaryKey val id: String, val sessionId: String, val exerciseId: String, val setIndex: Int, val reps: Int? = null, val loadKg: Double? = null, val completed: Boolean = false, val increaseMarked: Boolean = false)

@Entity(tableName = "app_settings")
data class AppSettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "training_periods")
data class TrainingPeriodEntity(@PrimaryKey val id: String, val title: String, val startedAt: Long, val active: Boolean = true)

@Entity(tableName = "workout_period_links", primaryKeys = ["workoutId", "periodId"])
data class WorkoutPeriodLinkEntity(val workoutId: String, val periodId: String)

data class WorkoutExerciseRow(val exerciseId: String, val name: String, val muscleGroup: String, val restSeconds: Int, val plannedReps: String, val setCount: Int, val plannedLoadsCsv: String)

@Dao
interface GymDao {
    @Query("SELECT * FROM workouts ORDER BY sortOrder") fun observeWorkouts(): Flow<List<WorkoutEntity>>
    @Query("SELECT * FROM exercises ORDER BY name") fun observeExercises(): Flow<List<ExerciseEntity>>
    @Query("SELECT COUNT(*) FROM workouts") suspend fun workoutCount(): Int
    @Query("SELECT * FROM workout_exercises we INNER JOIN exercises e ON e.id = we.exerciseId WHERE we.workoutId = :workoutId ORDER BY we.sortOrder") fun observeWorkoutExercises(workoutId: String): Flow<List<WorkoutExerciseRow>>
    @Query("SELECT * FROM exercise_load_profiles WHERE exerciseId = :exerciseId ORDER BY setIndex") fun observeLoadProfile(exerciseId: String): Flow<List<ExerciseLoadProfileEntity>>
    @Query("SELECT COUNT(*) FROM workout_sessions WHERE completed = 1") fun observeCompletedSessionCount(): Flow<Int>
    @Query("SELECT * FROM workout_sessions WHERE completed = 1 ORDER BY finishedAt") fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM session_sets WHERE exerciseId = :exerciseId AND completed = 1 AND loadKg IS NOT NULL ORDER BY rowid") fun observeExerciseHistory(exerciseId: String): Flow<List<SessionSetEntity>>
    @Query("SELECT * FROM app_settings") fun observeSettings(): Flow<List<AppSettingEntity>>
    @Query("SELECT * FROM training_periods WHERE active = 1 ORDER BY startedAt DESC LIMIT 1") fun observeActivePeriod(): Flow<TrainingPeriodEntity?>
    @Query("SELECT * FROM exercises") suspend fun allExercises(): List<ExerciseEntity>
    @Query("SELECT * FROM workouts") suspend fun allWorkouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercises") suspend fun allWorkoutExercises(): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM exercise_load_profiles") suspend fun allLoadProfiles(): List<ExerciseLoadProfileEntity>
    @Query("SELECT * FROM workout_sessions") suspend fun allSessions(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM session_sets") suspend fun allSessionSets(): List<SessionSetEntity>
    @Query("SELECT * FROM app_settings") suspend fun allSettings(): List<AppSettingEntity>
    @Query("SELECT * FROM training_periods") suspend fun allPeriods(): List<TrainingPeriodEntity>
    @Query("SELECT * FROM workout_period_links") suspend fun allPeriodLinks(): List<WorkoutPeriodLinkEntity>
    @Query("SELECT MAX(sortOrder) FROM workouts") suspend fun maxWorkoutOrder(): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercises(items: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertExercisesIfMissing(items: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWorkouts(items: List<WorkoutEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWorkoutExercises(items: List<WorkoutExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWorkoutExercisesIfMissing(items: List<WorkoutExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLoadProfile(items: List<ExerciseLoadProfileEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSession(session: WorkoutSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSessionSet(set: SessionSetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSetting(setting: AppSettingEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPeriod(period: TrainingPeriodEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPeriodLink(link: WorkoutPeriodLinkEntity)
    @Query("UPDATE training_periods SET active = 0") suspend fun deactivatePeriods()
    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId") suspend fun deleteWorkoutExercises(workoutId: String)
    @Query("DELETE FROM workouts WHERE id = :workoutId") suspend fun deleteWorkout(workoutId: String)
    @Query("UPDATE workout_exercises SET restSeconds = :restSeconds, setCount = :setCount WHERE workoutId = :workoutId AND exerciseId = :exerciseId") suspend fun updateWorkoutExercise(workoutId: String, exerciseId: String, restSeconds: Int, setCount: Int)
    @Query("UPDATE workout_exercises SET plannedLoadsCsv = :loads WHERE workoutId = :workoutId AND exerciseId = :exerciseId") suspend fun updatePlannedLoads(workoutId: String, exerciseId: String, loads: String)
    @Query("UPDATE workout_sessions SET finishedAt = :finishedAt, completed = 1 WHERE id = :sessionId") suspend fun finishSession(sessionId: String, finishedAt: Long)
}

@Database(entities = [ExerciseEntity::class, WorkoutEntity::class, WorkoutExerciseEntity::class, ExerciseLoadProfileEntity::class, WorkoutSessionEntity::class, SessionSetEntity::class, AppSettingEntity::class, TrainingPeriodEntity::class, WorkoutPeriodLinkEntity::class], version = 3, exportSchema = false)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao
    companion object {
        fun create(context: Context): GymDatabase = Room.databaseBuilder(context, GymDatabase::class.java, "gym_app.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS training_periods (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, startedAt INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_period_links (workoutId TEXT NOT NULL, periodId TEXT NOT NULL, PRIMARY KEY(workoutId, periodId))")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN setCount INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN plannedLoadsCsv TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_sessions (id TEXT NOT NULL PRIMARY KEY, workoutId TEXT NOT NULL, startedAt INTEGER NOT NULL, finishedAt INTEGER, completed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS session_sets (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, exerciseId TEXT NOT NULL, setIndex INTEGER NOT NULL, reps INTEGER, loadKg REAL, completed INTEGER NOT NULL DEFAULT 0, increaseMarked INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
    }
}

suspend fun GymDao.seedIfEmpty() {
    if (workoutCount() > 0) return
    val exercises = listOf(
        ExerciseEntity("plantar-arch", "Manutenção do arco plantar", "Preparação"), ExerciseEntity("ankle-mobility", "Mobilidade de tornozelo", "Preparação"), ExerciseEntity("bosu-crunch", "Abdominal infra no bosu", "Abdômen"), ExerciseEntity("supinated-pulldown", "Puxada supinada", "Costas"), ExerciseEntity("pronated-row", "Remada máquina pegada pronada", "Costas"), ExerciseEntity("neutral-row", "Remada máquina pegada neutra", "Costas"), ExerciseEntity("convergent-row", "Serrote convergente", "Costas"), ExerciseEntity("face-pull", "Face pull", "Ombros"), ExerciseEntity("dumbbell-bench", "Supino reto com halteres", "Peito"), ExerciseEntity("incline-machine-press", "Supino inclinado máquina", "Peito"), ExerciseEntity("machine-shoulder-press", "Desenvolvimento máquina", "Ombros"), ExerciseEntity("lateral-raise", "Elevação lateral halteres", "Ombros"), ExerciseEntity("hip-thrust", "Elevação pélvica máquina", "Glúteos"), ExerciseEntity("hack-squat", "Hack machine", "Pernas"), ExerciseEntity("leg-extension", "Cadeira extensora", "Quadríceps"), ExerciseEntity("leg-curl", "Cadeira flexora", "Posterior"), ExerciseEntity("free-squat", "Agachamento livre", "Pernas"), ExerciseEntity("leg-press", "Leg press horizontal", "Pernas"), ExerciseEntity("stiff", "Stiff", "Posterior"), ExerciseEntity("walk", "Caminhada", "Cardio")
    )
    val workouts = listOf(WorkoutEntity("upper-1", "Upper", "Costas · Peito · Ombros · Braços", 1), WorkoutEntity("lower-1", "Lower", "Quadríceps · Posterior · Glúteos", 2), WorkoutEntity("cardio-1", "Cardio", "Caminhada · 6 km · 60 min", 3), WorkoutEntity("upper-2", "Upper 2", "Costas · Peito · Ombros · Braços", 4), WorkoutEntity("lower-2", "Lower 2", "Quadríceps · Posterior · Glúteos", 5))
    val links = listOf(
        WorkoutExerciseEntity("upper-1", "ankle-mobility", 1, 30, "10–12", 2), WorkoutExerciseEntity("upper-1", "bosu-crunch", 2, 60, "10–12"), WorkoutExerciseEntity("upper-1", "supinated-pulldown", 3, 90, "10–12", 3, "8,6,7"), WorkoutExerciseEntity("upper-1", "pronated-row", 4, 90, "10–12"), WorkoutExerciseEntity("upper-1", "convergent-row", 5, 90, "10–12"), WorkoutExerciseEntity("upper-1", "face-pull", 6, 60, "10–12"), WorkoutExerciseEntity("upper-1", "dumbbell-bench", 7, 90, "10–12", 4), WorkoutExerciseEntity("upper-1", "incline-machine-press", 8, 90, "10–12"), WorkoutExerciseEntity("upper-1", "machine-shoulder-press", 9, 90, "10–12"), WorkoutExerciseEntity("upper-1", "lateral-raise", 10, 60, "10–12"),
        WorkoutExerciseEntity("lower-1", "ankle-mobility", 1, 30, "10–12", 2), WorkoutExerciseEntity("lower-1", "hip-thrust", 2, 120, "10–12", 4, "55,60,60,60"), WorkoutExerciseEntity("lower-1", "free-squat", 3, 120, "10–12", 4), WorkoutExerciseEntity("lower-1", "leg-press", 4, 120, "10–12", 4), WorkoutExerciseEntity("lower-1", "leg-curl", 5, 90, "10–12", 4), WorkoutExerciseEntity("lower-1", "leg-extension", 6, 90, "10–12"), WorkoutExerciseEntity("lower-1", "stiff", 7, 120, "10–12", 4),
        WorkoutExerciseEntity("cardio-1", "walk", 1, 0, "60 min", 1),
        WorkoutExerciseEntity("upper-2", "ankle-mobility", 1, 30, "10–12", 2), WorkoutExerciseEntity("upper-2", "supinated-pulldown", 2, 90, "10–12"), WorkoutExerciseEntity("upper-2", "neutral-row", 3, 90, "10–12"), WorkoutExerciseEntity("upper-2", "machine-shoulder-press", 4, 90, "10–12"),
        WorkoutExerciseEntity("lower-2", "plantar-arch", 1, 30, "15–20"), WorkoutExerciseEntity("lower-2", "hip-thrust", 2, 120, "10–12", 3, "55,55,55"), WorkoutExerciseEntity("lower-2", "hack-squat", 3, 120, "10–12"), WorkoutExerciseEntity("lower-2", "leg-extension", 4, 90, "10–12"), WorkoutExerciseEntity("lower-2", "leg-curl", 5, 90, "10–12")
    )
    insertExercises(exercises); insertWorkouts(workouts); insertWorkoutExercises(links)
}

suspend fun GymDao.ensureCatalog() {
    if (allSettings().none { it.key == "period_id" }) {
        val period = TrainingPeriodEntity("period-inicial", "Programa inicial", System.currentTimeMillis())
        insertPeriod(period)
        saveSetting(AppSettingEntity("period_id", period.id))
        saveSetting(AppSettingEntity("period_title", period.title))
        allWorkouts().forEach { insertPeriodLink(WorkoutPeriodLinkEntity(it.id, period.id)) }
    }
    insertExercisesIfMissing(listOf(
        ExerciseEntity("scapula-cadence", "Aula posicionamento das escápulas e cadência", "Preparação"),
        ExerciseEntity("foot-spacing", "Aula afastamento dos pés", "Preparação"),
        ExerciseEntity("foot-support", "Aula apoio dos pés", "Preparação"),
        ExerciseEntity("arch-class", "Aula arco plantar", "Preparação"),
        ExerciseEntity("bracing", "Manobra do bracing", "Preparação"),
        ExerciseEntity("triceps-french", "Tríceps francês banco inclinado 30°", "Tríceps"),
        ExerciseEntity("triceps-forehead", "Tríceps testa halteres", "Tríceps"),
        ExerciseEntity("close-grip-dumbbell", "Supino fechado halteres", "Peito"),
        ExerciseEntity("machine-bench", "Supino reto máquina", "Peito"),
        ExerciseEntity("standing-calf", "Panturrilha em pé", "Panturrilha"),
        ExerciseEntity("scott-curl", "Rosca Scott máquina", "Bíceps"),
        ExerciseEntity("dumbbell-curl", "Rosca halteres", "Bíceps")
    ))
    insertWorkoutExercisesIfMissing(listOf(
        WorkoutExerciseEntity("upper-1", "scapula-cadence", 11, 45, "10–12", 2),
        WorkoutExerciseEntity("upper-1", "triceps-french", 12, 75, "10–12", 3),
        WorkoutExerciseEntity("lower-1", "foot-spacing", 8, 30, "0", 1),
        WorkoutExerciseEntity("lower-1", "foot-support", 9, 30, "0", 1),
        WorkoutExerciseEntity("lower-1", "arch-class", 10, 30, "0", 1),
        WorkoutExerciseEntity("lower-1", "dumbbell-curl", 11, 60, "10–12", 3),
        WorkoutExerciseEntity("upper-2", "bracing", 5, 45, "0", 1),
        WorkoutExerciseEntity("upper-2", "scapula-cadence", 6, 45, "10–12", 2),
        WorkoutExerciseEntity("upper-2", "bosu-crunch", 7, 60, "10–12", 3),
        WorkoutExerciseEntity("upper-2", "machine-bench", 8, 90, "10–12", 3),
        WorkoutExerciseEntity("upper-2", "close-grip-dumbbell", 9, 90, "10–12", 3),
        WorkoutExerciseEntity("upper-2", "lateral-raise", 10, 60, "10–12", 3),
        WorkoutExerciseEntity("upper-2", "triceps-forehead", 11, 75, "10–12", 3),
        WorkoutExerciseEntity("lower-2", "foot-spacing", 6, 30, "0", 1),
        WorkoutExerciseEntity("lower-2", "foot-support", 7, 30, "0", 1),
        WorkoutExerciseEntity("lower-2", "arch-class", 8, 30, "0", 1),
        WorkoutExerciseEntity("lower-2", "standing-calf", 9, 90, "10–12", 3),
        WorkoutExerciseEntity("lower-2", "scott-curl", 10, 75, "10–12", 3)
    ))
    val defaultLoads = mapOf(
        "supinated-pulldown" to listOf(6.0, 6.0, 7.0),
        "pronated-row" to listOf(30.0, 35.0, 35.0),
        "convergent-row" to listOf(22.5, 27.5, 27.5),
        "face-pull" to listOf(7.0, 7.0, 7.0),
        "dumbbell-bench" to listOf(30.0, 32.5, 35.0, 35.0),
        "incline-machine-press" to listOf(20.0, 22.5, 25.0),
        "machine-shoulder-press" to listOf(10.0, 10.0, 12.5),
        "lateral-raise" to listOf(9.0, 9.0, 9.0),
        "triceps-french" to listOf(20.0, 20.0, 20.0),
        "neutral-row" to listOf(22.5, 22.5, 25.0),
        "machine-bench" to listOf(22.5, 22.5, 25.0),
        "close-grip-dumbbell" to listOf(30.0, 35.0, 35.0),
        "triceps-forehead" to listOf(10.0, 10.0, 10.0),
        "hip-thrust" to listOf(60.0, 60.0, 60.0, 90.0),
        "free-squat" to listOf(15.0, 15.0, 20.0, 20.0),
        "leg-press" to listOf(6.0, 6.0, 7.0, 7.0),
        "leg-curl" to listOf(15.0, 15.0, 17.5, 17.5),
        "leg-extension" to listOf(13.0, 13.0, 14.0),
        "stiff" to listOf(10.0, 10.0, 10.0, 10.0),
        "dumbbell-curl" to listOf(10.0, 10.0, 10.0),
        "hack-squat" to listOf(20.0, 20.0, 22.5),
        "standing-calf" to listOf(15.0, 15.0, 15.0),
        "scott-curl" to listOf(12.0, 12.0, 12.0)
    )
    val existing = allLoadProfiles().map { "${it.exerciseId}:${it.setIndex}" }.toSet()
    defaultLoads.flatMap { (exerciseId, loads) -> loads.mapIndexed { index, value -> ExerciseLoadProfileEntity(exerciseId, index + 1, value) } }
        .filterNot { "${it.exerciseId}:${it.setIndex}" in existing }
        .let { if (it.isNotEmpty()) saveLoadProfile(it) }
    val plannedByWorkout = mapOf(
        "upper-1:ankle-mobility" to "", "upper-1:supinated-pulldown" to "6,6,7", "upper-1:pronated-row" to "30,35,35", "upper-1:convergent-row" to "22.5,27.5,27.5", "upper-1:face-pull" to "7,7,7", "upper-1:dumbbell-bench" to "30,32.5,35,35", "upper-1:incline-machine-press" to "20,22.5,25", "upper-1:machine-shoulder-press" to "10,10,12.5", "upper-1:lateral-raise" to "9,9,9", "upper-1:triceps-french" to "20,20,20",
        "upper-2:supinated-pulldown" to "8,6,7", "upper-2:neutral-row" to "22.5,22.5,25", "upper-2:machine-bench" to "22.5,22.5,25", "upper-2:machine-shoulder-press" to "10,10,12.5", "upper-2:close-grip-dumbbell" to "30,35,35", "upper-2:lateral-raise" to "9,9,9", "upper-2:triceps-forehead" to "10,10,10",
        "lower-1:hip-thrust" to "60,60,60,90", "lower-1:free-squat" to "15,15,20,20", "lower-1:leg-press" to "6,6,7,7", "lower-1:leg-curl" to "15,15,17.5,17.5", "lower-1:leg-extension" to "13,13,14", "lower-1:stiff" to "10,10,10,10", "lower-1:dumbbell-curl" to "10,10,10",
        "lower-2:hip-thrust" to "55,55,55", "lower-2:hack-squat" to "20,20,22.5", "lower-2:leg-extension" to "13,14,14", "lower-2:leg-curl" to "15,15,17.5", "lower-2:standing-calf" to "15,15,15", "lower-2:scott-curl" to "12,12,12"
    )
    plannedByWorkout.forEach { (key, loads) -> val parts = key.split(":"); updatePlannedLoads(parts[0], parts[1], loads) }
}
