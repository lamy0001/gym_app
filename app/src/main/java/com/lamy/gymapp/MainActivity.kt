package com.lamy.gymapp

import android.os.Bundle
import android.content.Context
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.lamy.gymapp.data.GymDatabase
import com.lamy.gymapp.data.WorkoutEntity
import com.lamy.gymapp.data.ExerciseEntity
import com.lamy.gymapp.data.WorkoutExerciseRow
import com.lamy.gymapp.data.TrainingPeriodEntity
import com.lamy.gymapp.data.WorkoutPeriodLinkEntity
import com.lamy.gymapp.data.duplicateWorkoutTemplates
import com.lamy.gymapp.data.sessionsInPeriod
import com.lamy.gymapp.data.scheduledDaysThisWeek
import com.lamy.gymapp.data.peakLoadsBySession
import com.lamy.gymapp.data.seedIfEmpty
import com.lamy.gymapp.data.ensureCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Green = Color(0xFF47714D)
private val SoftGreen = Color(0xFFDFEDDF)
private val AppBackground = Color(0xFFF1F5F0)

private const val ReminderRequestCode = 4107

private fun scheduleReminder(context: Context, enabled: Boolean, time: String) {
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(context, ReminderRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(AlarmManager::class.java)
    alarm.cancel(pending)
    if (!enabled) return
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val next = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
}

class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        if (day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("workout_reminders", "Lembretes de treino", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = androidx.core.app.NotificationCompat.Builder(context, "workout_reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hora do treino")
            .setContentText("Seu treino programado está esperando por você.")
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(4107, notification) }
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GymViewModel> {
        GymViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4108)
        viewModel.syncReminder(this)
        setContent { GymApp(viewModel) }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GymViewModel(private val database: GymDatabase) : ViewModel() {
    val workouts: Flow<List<WorkoutEntity>> = database.dao().observeWorkouts()
    val catalog: Flow<List<ExerciseEntity>> = database.dao().observeExercises()
    val completedSessionCount: Flow<Int> = database.dao().observeCompletedSessionCount()
    val completedSessions: Flow<List<com.lamy.gymapp.data.WorkoutSessionEntity>> = database.dao().observeCompletedSessions()
    val settings: Flow<List<com.lamy.gymapp.data.AppSettingEntity>> = database.dao().observeSettings()
    val activePeriod: Flow<TrainingPeriodEntity?> = database.dao().observeActivePeriod()
    val periods: Flow<List<TrainingPeriodEntity>> = database.dao().observePeriods()
    val activePeriodWorkouts: Flow<List<WorkoutEntity>> = activePeriod.flatMapLatest { period ->
        period?.let { database.dao().observeWorkoutsForPeriod(it.id) } ?: flowOf(emptyList())
    }
    private val _selectedWorkoutId = MutableStateFlow<String?>(null)
    val selectedWorkoutId: StateFlow<String?> = _selectedWorkoutId
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId

    init { viewModelScope.launch { database.dao().seedIfEmpty(); database.dao().ensureCatalog() } }

    fun selectWorkout(id: String) { _selectedWorkoutId.value = id }

    fun startSession(workoutId: String, periodId: String?) {
        val id = UUID.randomUUID().toString()
        _activeSessionId.value = id
        viewModelScope.launch { database.dao().insertSession(com.lamy.gymapp.data.WorkoutSessionEntity(id, workoutId, System.currentTimeMillis(), periodId = periodId)) }
    }

    fun finishSession() {
        _activeSessionId.value?.let { id ->
            _activeSessionId.value = null
            viewModelScope.launch { database.dao().finishSession(id, System.currentTimeMillis()) }
        }
    }

    fun saveSessionSet(sessionId: String, exerciseId: String, setIndex: Int, loadKg: Double?, completed: Boolean, increaseMarked: Boolean) {
        viewModelScope.launch {
            database.dao().saveSessionSet(com.lamy.gymapp.data.SessionSetEntity("$sessionId-$exerciseId-$setIndex", sessionId, exerciseId, setIndex, loadKg = loadKg, completed = completed, increaseMarked = increaseMarked))
            if (loadKg != null) saveLoad(exerciseId, setIndex, loadKg)
        }
    }

    fun removeWorkoutFromPeriod(workoutId: String, periodId: String?) {
        if (periodId == null) return
        viewModelScope.launch { database.dao().unlinkWorkout(workoutId, periodId) }
    }

    fun updateWorkoutExercise(workoutId: String, exerciseId: String, restSeconds: Int, setCount: Int) {
        viewModelScope.launch { database.dao().updateWorkoutExercise(workoutId, exerciseId, restSeconds, setCount) }
    }

    fun updateWorkoutExerciseDetails(workoutId: String, exerciseId: String, restSeconds: Int, setCount: Int, plannedReps: String) {
        viewModelScope.launch { database.dao().updateWorkoutExerciseDetails(workoutId, exerciseId, restSeconds, setCount, plannedReps) }
    }

    fun createWorkout(title: String, subtitle: String, periodId: String?) {
        viewModelScope.launch {
            val dao = database.dao()
            val order = (periodId?.let { dao.maxWorkoutOrderForPeriod(it) } ?: dao.maxWorkoutOrder() ?: 0) + 1
            val workout = WorkoutEntity(UUID.randomUUID().toString(), title.ifBlank { "Novo treino" }, subtitle.ifBlank { "Personalizado" }, order)
            dao.insertWorkouts(listOf(workout))
            periodId?.let { dao.insertPeriodLink(WorkoutPeriodLinkEntity(workout.id, it)) }
        }
    }

    fun workoutsForPeriod(periodId: String) = database.dao().observeWorkoutsForPeriod(periodId)

    fun addExercise(workoutId: String, exerciseId: String) {
        viewModelScope.launch {
            val rest = database.dao().allSettings().firstOrNull { it.key == "defaultRestSeconds" }?.value?.toIntOrNull() ?: 90
            val order = database.dao().allWorkoutExercises().count { it.workoutId == workoutId } + 1
            database.dao().insertWorkoutExercisesIfMissing(listOf(com.lamy.gymapp.data.WorkoutExerciseEntity(workoutId, exerciseId, order, rest, "10–12", 3)))
        }
    }

    fun createNewPeriod(title: String, copyCurrentWorkouts: Boolean) {
        viewModelScope.launch {
            val dao = database.dao()
            database.withTransaction {
                val currentId = dao.allSettings().firstOrNull { it.key == "period_id" }?.value
                val previousWorkouts = if (copyCurrentWorkouts && currentId != null) dao.workoutsForPeriod(currentId) else emptyList()
                val newPeriod = TrainingPeriodEntity(UUID.randomUUID().toString(), title.trim().ifBlank { "Novo período" }, System.currentTimeMillis(), true)
                dao.deactivatePeriods()
                dao.insertPeriod(newPeriod)
                val templates = previousWorkouts.map { workout -> workout to dao.workoutExercises(workout.id) }
                duplicateWorkoutTemplates(templates) { UUID.randomUUID().toString() }.forEach { copy ->
                    dao.insertWorkouts(listOf(copy.workout))
                    if (copy.exercises.isNotEmpty()) dao.insertWorkoutExercises(copy.exercises)
                    dao.insertPeriodLink(WorkoutPeriodLinkEntity(copy.workout.id, newPeriod.id))
                }
                dao.activatePeriod(newPeriod.id)
                dao.saveSetting(com.lamy.gymapp.data.AppSettingEntity("period_id", newPeriod.id))
                dao.saveSetting(com.lamy.gymapp.data.AppSettingEntity("period_title", newPeriod.title))
            }
        }
    }

    fun selectPeriod(period: TrainingPeriodEntity) {
        viewModelScope.launch {
            val dao = database.dao()
            database.withTransaction {
                dao.deactivatePeriods()
                dao.activatePeriod(period.id)
                dao.saveSetting(com.lamy.gymapp.data.AppSettingEntity("period_id", period.id))
                dao.saveSetting(com.lamy.gymapp.data.AppSettingEntity("period_title", period.title))
            }
        }
    }

    fun renameActivePeriod(periodId: String, title: String) {
        viewModelScope.launch {
            val normalized = title.trim().ifBlank { return@launch }
            database.dao().renamePeriod(periodId, normalized)
            database.dao().saveSetting(com.lamy.gymapp.data.AppSettingEntity("period_title", normalized))
        }
    }

    fun exercises(workoutId: String): Flow<List<WorkoutExerciseRow>> =
        database.dao().observeWorkoutExercises(workoutId)

    fun loadProfile(exerciseId: String) = database.dao().observeLoadProfile(exerciseId)

    fun exerciseHistory(exerciseId: String, periodId: String?) = database.dao().observeExerciseHistory(exerciseId, periodId)

    fun saveSetting(key: String, value: String) {
        viewModelScope.launch { database.dao().saveSetting(com.lamy.gymapp.data.AppSettingEntity(key, value)) }
    }

    fun syncReminder(context: Context) {
        viewModelScope.launch {
            val values = database.dao().allSettings().associate { it.key to it.value }
            scheduleReminder(context, values["remindersEnabled"] != "false", values["reminderTime"] ?: "07:00")
        }
    }

    fun exportBackup(context: Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val dao = database.dao()
            val root = JSONObject().put("format", "gym-app-backup").put("version", 1)
            fun array(block: JSONArray.() -> Unit) = JSONArray().apply(block)
            val exercises = dao.allExercises(); val workouts = dao.allWorkouts(); val links = dao.allWorkoutExercises(); val profiles = dao.allLoadProfiles(); val sessions = dao.allSessions(); val sessionSets = dao.allSessionSets(); val settings = dao.allSettings(); val periods = dao.allPeriods(); val periodLinks = dao.allPeriodLinks()
            root.put("exercises", array { exercises.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("muscleGroup", it.muscleGroup)) } })
            root.put("workouts", array { workouts.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("subtitle", it.subtitle).put("sortOrder", it.sortOrder)) } })
            root.put("workoutExercises", array { links.forEach { put(JSONObject().put("workoutId", it.workoutId).put("exerciseId", it.exerciseId).put("sortOrder", it.sortOrder).put("restSeconds", it.restSeconds).put("plannedReps", it.plannedReps).put("setCount", it.setCount).put("plannedLoadsCsv", it.plannedLoadsCsv)) } })
            root.put("loadProfiles", array { profiles.forEach { put(JSONObject().put("exerciseId", it.exerciseId).put("setIndex", it.setIndex).put("lastUsedLoadKg", it.lastUsedLoadKg)) } })
            root.put("sessions", array { sessions.forEach { put(JSONObject().put("id", it.id).put("workoutId", it.workoutId).put("startedAt", it.startedAt).put("finishedAt", it.finishedAt ?: JSONObject.NULL).put("completed", it.completed).put("periodId", it.periodId ?: JSONObject.NULL)) } })
            root.put("sessionSets", array { sessionSets.forEach { put(JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("exerciseId", it.exerciseId).put("setIndex", it.setIndex).put("reps", it.reps ?: JSONObject.NULL).put("loadKg", it.loadKg ?: JSONObject.NULL).put("completed", it.completed).put("increaseMarked", it.increaseMarked)) } })
            root.put("settings", array { settings.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) } })
            root.put("periods", array { periods.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("startedAt", it.startedAt).put("active", it.active)) } })
            root.put("periodLinks", array { periodLinks.forEach { put(JSONObject().put("workoutId", it.workoutId).put("periodId", it.periodId)) } })
            val success = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) } != null }.getOrDefault(false)
            onDone(success)
        }
    }

    fun importBackup(context: Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("backup vazio")
                val root = JSONObject(json)
                require(root.optString("format") == "gym-app-backup")
                val dao = database.dao()
                val exercises = (0 until root.getJSONArray("exercises").length()).map { val o = root.getJSONArray("exercises").getJSONObject(it); com.lamy.gymapp.data.ExerciseEntity(o.getString("id"), o.getString("name"), o.getString("muscleGroup")) }
                val workouts = (0 until root.getJSONArray("workouts").length()).map { val o = root.getJSONArray("workouts").getJSONObject(it); WorkoutEntity(o.getString("id"), o.getString("title"), o.getString("subtitle"), o.getInt("sortOrder")) }
                val links = (0 until root.getJSONArray("workoutExercises").length()).map { val o = root.getJSONArray("workoutExercises").getJSONObject(it); com.lamy.gymapp.data.WorkoutExerciseEntity(o.getString("workoutId"), o.getString("exerciseId"), o.getInt("sortOrder"), o.getInt("restSeconds"), o.getString("plannedReps"), o.optInt("setCount", 3), o.optString("plannedLoadsCsv", "")) }
                val profiles = (0 until root.getJSONArray("loadProfiles").length()).map { val o = root.getJSONArray("loadProfiles").getJSONObject(it); com.lamy.gymapp.data.ExerciseLoadProfileEntity(o.getString("exerciseId"), o.getInt("setIndex"), o.getDouble("lastUsedLoadKg")) }
                val sessions = (0 until root.getJSONArray("sessions").length()).map { val o = root.getJSONArray("sessions").getJSONObject(it); com.lamy.gymapp.data.WorkoutSessionEntity(o.getString("id"), o.getString("workoutId"), o.getLong("startedAt"), if (o.isNull("finishedAt")) null else o.getLong("finishedAt"), o.getBoolean("completed"), if (o.isNull("periodId")) null else o.optString("periodId").takeIf(String::isNotBlank)) }
                val sessionSets = (0 until root.getJSONArray("sessionSets").length()).map { val o = root.getJSONArray("sessionSets").getJSONObject(it); com.lamy.gymapp.data.SessionSetEntity(o.getString("id"), o.getString("sessionId"), o.getString("exerciseId"), o.getInt("setIndex"), if (o.isNull("reps")) null else o.getInt("reps"), if (o.isNull("loadKg")) null else o.getDouble("loadKg"), o.getBoolean("completed"), o.getBoolean("increaseMarked")) }
                val settings = (0 until root.getJSONArray("settings").length()).map { val o = root.getJSONArray("settings").getJSONObject(it); com.lamy.gymapp.data.AppSettingEntity(o.getString("key"), o.getString("value")) }
                val periodArray = root.optJSONArray("periods")
                val periodLinkArray = root.optJSONArray("periodLinks")
                val periods = if (periodArray == null) emptyList() else (0 until periodArray.length()).map { val o = periodArray.getJSONObject(it); TrainingPeriodEntity(o.getString("id"), o.getString("title"), o.getLong("startedAt"), o.optBoolean("active", true)) }
                val periodLinks = if (periodLinkArray == null) emptyList() else (0 until periodLinkArray.length()).map { val o = periodLinkArray.getJSONObject(it); WorkoutPeriodLinkEntity(o.getString("workoutId"), o.getString("periodId")) }
                dao.insertExercises(exercises); dao.insertWorkouts(workouts); dao.insertWorkoutExercises(links); dao.saveLoadProfile(profiles)
                periods.forEach { dao.insertPeriod(it) }; periodLinks.forEach { dao.insertPeriodLink(it) }
                sessions.forEach { dao.insertSession(it) }; sessionSets.forEach { dao.saveSessionSet(it) }; settings.forEach { dao.saveSetting(it) }
            }.isSuccess
            onDone(success)
        }
    }

    fun saveLoad(exerciseId: String, setIndex: Int, value: Double) {
        viewModelScope.launch {
            database.dao().saveLoadProfile(
                listOf(com.lamy.gymapp.data.ExerciseLoadProfileEntity(exerciseId, setIndex, value))
            )
        }
    }

    companion object {
        fun factory(context: android.content.Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = GymDatabase.create(context)
                @Suppress("UNCHECKED_CAST")
                return GymViewModel(db) as T
            }
        }
    }
}

@Composable
fun GymApp(viewModel: GymViewModel) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activePeriod = viewModel.activePeriod.collectAsStateWithLifecycle(initialValue = null).value
    val periods = viewModel.periods.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val periodWorkouts = viewModel.activePeriodWorkouts.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val selectedId by viewModel.selectedWorkoutId.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf("home") }

    BackHandler(enabled = screen != "home") {
        when (screen) {
            "edit" -> screen = "workouts"
            "workout" -> { viewModel.finishSession(); screen = "home" }
            else -> screen = "home"
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
            if (screen == "workout" && selectedId != null) {
                val sessionId = viewModel.activeSessionId.collectAsStateWithLifecycle().value
                val leaveWorkout = {
                    viewModel.finishSession()
                    screen = "home"
                }
                WorkoutScreen(
                    viewModel = viewModel,
                    workoutId = selectedId!!,
                    sessionId = sessionId,
                    workout = workouts.firstOrNull { it.id == selectedId },
                    exercises = viewModel.exercises(selectedId!!).collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    onBack = leaveWorkout,
                    onStartSession = { viewModel.startSession(selectedId!!, activePeriod?.id) },
                    onFinishSession = leaveWorkout
                )
            } else if (screen == "settings") {
                SettingsScreen(viewModel = viewModel, onBack = { screen = "home" }, onEditWorkouts = { screen = "workouts" })
            } else if (screen == "workouts") {
                WorkoutsScreen(
                    workouts = periodWorkouts,
                    activePeriod = activePeriod,
                    periods = periods,
                    onBack = { screen = "home" },
                    onOpenWorkout = { id -> viewModel.finishSession(); viewModel.selectWorkout(id); screen = "workout" },
                    onEditWorkout = { id -> viewModel.selectWorkout(id); screen = "edit" },
                    onRemoveWorkout = { id -> viewModel.removeWorkoutFromPeriod(id, activePeriod?.id) },
                    onCreateWorkout = { title, subtitle -> viewModel.createWorkout(title, subtitle, activePeriod?.id) },
                    onCreatePeriod = viewModel::createNewPeriod,
                    onSelectPeriod = viewModel::selectPeriod
                )
            } else if (screen == "edit" && selectedId != null) {
                EditWorkoutScreen(workout = workouts.firstOrNull { it.id == selectedId }, exercises = viewModel.exercises(selectedId!!).collectAsStateWithLifecycle(initialValue = emptyList()).value, catalog = viewModel.catalog.collectAsStateWithLifecycle(initialValue = emptyList()).value, onBack = { screen = "workouts" }, onAddExercise = { viewModel.addExercise(selectedId!!, it) }, onSave = { item, rest, sets, reps -> viewModel.updateWorkoutExerciseDetails(selectedId!!, item.exerciseId, rest, sets, reps) })
            } else if (screen == "history") {
                HistoryScreen(
                    viewModel = viewModel,
                    catalog = viewModel.catalog.collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    onBack = { screen = "home" },
                    sessions = viewModel.completedSessions.collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    periods = periods,
                    activePeriodId = activePeriod?.id
                )
            } else {
                HomeScreen(
                    workouts = periodWorkouts,
                    periodTitle = activePeriod?.title ?: "Programa inicial",
                    onOpenWorkout = { id -> viewModel.finishSession(); viewModel.selectWorkout(id); screen = "workout" },
                    onSettings = { screen = "settings" },
                    onWorkouts = { screen = "workouts" },
                    onHistory = { screen = "history" }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: GymViewModel, onBack: () -> Unit, onEditWorkouts: () -> Unit) {
    val context = LocalContext.current
    var backupMessage by rememberSaveable { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(context, it) { ok -> backupMessage = if (ok) "Backup exportado com sucesso." else "Não foi possível exportar o backup." } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(context, it) { ok -> backupMessage = if (ok) "Backup restaurado com sucesso." else "Arquivo de backup inválido." } }
    }
    val settings = viewModel.settings.collectAsStateWithLifecycle(initialValue = emptyList()).value.associate { it.key to it.value }
    val activePeriod = viewModel.activePeriod.collectAsStateWithLifecycle(initialValue = null).value
    var periodTitle by rememberSaveable(activePeriod?.id) { mutableStateOf(activePeriod?.title ?: settings["period_title"] ?: "Programa inicial") }
    var remindersEnabled by rememberSaveable(settings["remindersEnabled"]) { mutableStateOf(settings["remindersEnabled"] != "false") }
    var selectedRest by rememberSaveable(settings["defaultRestSeconds"]) { mutableIntStateOf(settings["defaultRestSeconds"]?.toIntOrNull() ?: 90) }
    var reminderTime by rememberSaveable(settings["reminderTime"]) { mutableStateOf(settings["reminderTime"] ?: "07:00") }

    Scaffold(containerColor = AppBackground) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(Modifier.weight(1f)) {
                    Text("PREFERÊNCIAS", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsRow("Tema visual", "Claro")
            SettingsRow("Unidade de peso", "kg")
            SettingsRow("Descanso padrão", "$selectedRest s")
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Lembrete do treino", fontWeight = FontWeight.Bold)
                    Text("07:00 · Segunda a sexta", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = remindersEnabled, onCheckedChange = { value -> remindersEnabled = value; viewModel.saveSetting("remindersEnabled", value.toString()); scheduleReminder(context, value, reminderTime) })
            }
            OutlinedTextField(
                value = reminderTime,
                onValueChange = { value -> reminderTime = value.take(5); viewModel.saveSetting("reminderTime", value.take(5)); scheduleReminder(context, remindersEnabled, value.take(5)) },
                label = { Text("Horário do lembrete") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Dias programados", color = Green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Sábado e domingo não quebram sua sequência.", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                listOf("S", "T", "Q", "Q", "S", "S", "D").forEachIndexed { index, day ->
                    Surface(
                        color = if (index < 5) Green else Color(0xFFE5EFEA),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(day, color = if (index < 5) Color.White else Color(0xFF71877E), modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Tempo de descanso", color = Green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                listOf(60, 90, 120).forEach { seconds ->
                        TextButton(onClick = { selectedRest = seconds; viewModel.saveSetting("defaultRestSeconds", seconds.toString()) }) {
                        Text("$seconds s", color = if (selectedRest == seconds) Green else Color(0xFF60786D), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsRow("Sons e vibração", "Ativo")
            SettingsRow("Editar treinos", "Abrir", onEditWorkouts)
            Spacer(Modifier.height(10.dp))
            Text("Sessão / período atual", color = Green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Use uma nova sessão quando seu programa de treino mudar. As cargas anteriores continuam disponíveis ao adicionar exercícios.", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            OutlinedTextField(value = periodTitle, onValueChange = { periodTitle = it; activePeriod?.let { period -> viewModel.renameActivePeriod(period.id, it) } }, label = { Text("Título do período") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton(onClick = onEditWorkouts) { Text("Gerenciar ciclos e treinos", color = Green, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            Text("Dados e segurança", color = Green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("O backup inclui treinos, cargas, sessões, histórico e configurações.", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { exportLauncher.launch("gym-app-backup.json") }) { Text("Exportar") }
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Restaurar") }
            }
            if (backupMessage.isNotBlank()) Text(backupMessage, color = Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = Green, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeScreen(
    workouts: List<WorkoutEntity>,
    periodTitle: String,
    onOpenWorkout: (String) -> Unit,
    onSettings: () -> Unit,
    onWorkouts: () -> Unit,
    onHistory: () -> Unit
) {
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("TREINA FÁCIL", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Escolha seu treino", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Configurações") }
            }
            Spacer(Modifier.height(18.dp))
            Text("$periodTitle · ${workouts.size} treinos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                workouts.forEach { workout ->
                    WorkoutCarouselCard(workout) { onOpenWorkout(workout.id) }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Menu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            MenuRow("▣", "Meus treinos", "Ver e iniciar os treinos deste período", onWorkouts)
            MenuRow("◷", "Histórico", "Frequência e evolução de cargas", onHistory)
        }
    }
}

@Composable
private fun MenuRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = Green, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = Green, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WorkoutsScreen(
    workouts: List<WorkoutEntity>,
    activePeriod: TrainingPeriodEntity?,
    periods: List<TrainingPeriodEntity>,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onEditWorkout: (String) -> Unit,
    onRemoveWorkout: (String) -> Unit,
    onCreateWorkout: (String, String) -> Unit,
    onCreatePeriod: (String, Boolean) -> Unit,
    onSelectPeriod: (TrainingPeriodEntity) -> Unit
) {
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    var showNewWorkout by remember { mutableStateOf(false) }
    var showPeriods by remember { mutableStateOf(false) }
    var showNewPeriod by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newSubtitle by remember { mutableStateOf("") }
    var newPeriodTitle by remember { mutableStateOf("") }
    var copyWorkouts by remember { mutableStateOf(true) }
    if (workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Excluir treino?") },
            text = { Text("${workoutToDelete!!.title} será removido deste período. O treino dos períodos anteriores e o histórico serão preservados.") },
            confirmButton = { TextButton(onClick = { onRemoveWorkout(workoutToDelete!!.id); workoutToDelete = null }) { Text("Remover", color = Color(0xFFB54D49)) } },
            dismissButton = { TextButton(onClick = { workoutToDelete = null }) { Text("Cancelar") } }
        )
    }
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Header("Meus treinos", activePeriod?.title ?: "Período não selecionado", onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showPeriods = true }, modifier = Modifier.weight(1f)) { Text("Trocar / ver períodos", color = Green) }
                TextButton(onClick = { newPeriodTitle = ""; copyWorkouts = true; showNewPeriod = true }, modifier = Modifier.weight(1f)) { Text("+ Novo período", color = Green) }
            }
            Button(onClick = { showNewWorkout = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar treino novo") }
            Text("${workouts.size} treinos neste período", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(workouts.size) { index ->
                    val workout = workouts[index]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenWorkout(workout.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${workout.sortOrder}", color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Treino ${workout.sortOrder} · ${workout.title}", fontWeight = FontWeight.Bold)
                                Text(workout.subtitle, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onEditWorkout(workout.id) }) { Text("✎", color = Green) }
                            TextButton(onClick = { workoutToDelete = workout }) { Text("♲", color = Color(0xFFB54D49)) }
                            Text("›", color = Green, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
    if (showNewWorkout) {
        AlertDialog(onDismissRequest = { showNewWorkout = false }, title = { Text("Adicionar treino") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Nome do treino") }, singleLine = true)
                OutlinedTextField(value = newSubtitle, onValueChange = { newSubtitle = it }, label = { Text("Grupos musculares / descrição") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = { onCreateWorkout(newTitle, newSubtitle); newTitle = ""; newSubtitle = ""; showNewWorkout = false }) { Text("Adicionar", color = Green) } }, dismissButton = { TextButton(onClick = { showNewWorkout = false }) { Text("Cancelar") } })
    }
    if (showPeriods) {
        PeriodPickerDialog(periods, activePeriod?.id, onDismiss = { showPeriods = false }, onSelect = { onSelectPeriod(it); showPeriods = false })
    }
    if (showNewPeriod) {
        AlertDialog(
            onDismissRequest = { showNewPeriod = false },
            title = { Text("Iniciar novo período") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newPeriodTitle, onValueChange = { newPeriodTitle = it }, label = { Text("Nome do período") }, placeholder = { Text("Ex.: Hipertrofia · Set–Nov") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copyWorkouts, onCheckedChange = { copyWorkouts = it })
                        Text("Copiar os treinos deste período", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("A cópia permite mudar exercícios e séries sem alterar o ciclo anterior. O histórico e as cargas por exercício continuam preservados e compartilhados.", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { onCreatePeriod(newPeriodTitle, copyWorkouts); showNewPeriod = false }) { Text("Criar período", color = Green) } },
            dismissButton = { TextButton(onClick = { showNewPeriod = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PeriodPickerDialog(periods: List<TrainingPeriodEntity>, selectedId: String?, onDismiss: () -> Unit, onSelect: (TrainingPeriodEntity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Períodos de treino") },
        text = {
            Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                periods.forEach { period ->
                    TextButton(onClick = { onSelect(period) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (period.id == selectedId) "✓ ATIVO · " else "") + period.title, color = if (period.id == selectedId) Green else Color(0xFF263A2B), fontWeight = FontWeight.Bold)
                            Text(java.text.DateFormat.getDateInstance().format(java.util.Date(period.startedAt)), color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (periods.isEmpty()) Text("Nenhum período cadastrado ainda.", color = Color(0xFF60786D))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Green) } }
    )
}

@Composable
private fun EditWorkoutScreen(
    workout: WorkoutEntity?,
    exercises: List<WorkoutExerciseRow>,
    catalog: List<ExerciseEntity>,
    onBack: () -> Unit,
    onAddExercise: (String) -> Unit,
    onSave: (WorkoutExerciseRow, Int, Int, String) -> Unit
) {
    var showAddExercise by remember { mutableStateOf(false) }
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Header(
                title = "Editar treino ${workout?.sortOrder ?: ""}",
                subtitle = workout?.title ?: "Treino",
                onBack = onBack
            )
            Text(
                "Ajuste séries e descanso por exercício. As alterações ficam salvas no aparelho.",
                color = Color(0xFF60786D),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(onClick = { showAddExercise = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar exercício") }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(exercises.size) { index ->
                    val item = exercises[index]
                    var restText by remember(item.exerciseId, item.restSeconds) { mutableStateOf(item.restSeconds.toString()) }
                    var setsText by remember(item.exerciseId, item.setCount) { mutableStateOf(item.setCount.toString()) }
                    var repsText by remember(item.exerciseId, item.plannedReps) { mutableStateOf(item.plannedReps) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.muscleGroup, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                                OutlinedTextField(
                                    value = repsText,
                                    onValueChange = { repsText = it.take(18) },
                                    label = { Text("Repetições") },
                                    placeholder = { Text("10–12") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.35f)
                                )
                                OutlinedTextField(
                                    value = restText,
                                    onValueChange = { restText = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Descanso (s)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = setsText,
                                    onValueChange = { setsText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Séries") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            TextButton(
                                onClick = {
                                    val rest = restText.toIntOrNull()?.coerceIn(0, 3600) ?: item.restSeconds
                                    val sets = setsText.toIntOrNull()?.coerceIn(1, 20) ?: item.setCount
                                    onSave(item, rest, sets, repsText.ifBlank { item.plannedReps })
                                    restText = rest.toString()
                                    setsText = sets.toString()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("Salvar exercício", color = Green, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
    if (showAddExercise) {
        AlertDialog(onDismissRequest = { showAddExercise = false }, title = { Text("Adicionar exercício") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                catalog.filter { item -> exercises.none { it.exerciseId == item.id } }.forEach { exercise ->
                    TextButton(onClick = { onAddExercise(exercise.id); showAddExercise = false }, modifier = Modifier.fillMaxWidth()) { Text(exercise.name, color = Color(0xFF29463B)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showAddExercise = false }) { Text("Fechar") } })
    }
}

@Composable
private fun HistoryScreen(
    viewModel: GymViewModel,
    catalog: List<ExerciseEntity>,
    onBack: () -> Unit,
    sessions: List<com.lamy.gymapp.data.WorkoutSessionEntity>,
    periods: List<TrainingPeriodEntity>,
    activePeriodId: String?
) {
    var selectedPeriodId by rememberSaveable(activePeriodId) { mutableStateOf(activePeriodId) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    val periodSessions = sessionsInPeriod(sessions, selectedPeriodId)
    val today = java.time.LocalDate.now()
    val exercises = catalog.filter { it.id != "walk" && it.muscleGroup != "Preparação" }
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Header("Histórico", "Frequência e evolução", onBack)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showPeriodPicker = true }) {
                    Text("Período: ${periods.firstOrNull { it.id == selectedPeriodId }?.title ?: "Selecione um período"}  ▾", color = Green, fontWeight = FontWeight.Bold)
                }
            }
            val selectedPeriodDays = scheduledDaysThisWeek(periodSessions, today, java.time.ZoneId.systemDefault())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("FREQUÊNCIA", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("$selectedPeriodDays / 5 dias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Seg–Sex", color = Color(0xFF527468), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5E0D5))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("TREINOS", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${periodSessions.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("concluídos no período", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Evolução por exercício", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text("Cada bloco mostra a maior carga de cada sessão. A escala é própria para cada exercício.", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(exercises.size) { index ->
                    ExerciseEvolutionCard(
                        exercise = exercises[index],
                        history = viewModel.exerciseHistory(exercises[index].id, selectedPeriodId).collectAsStateWithLifecycle(initialValue = emptyList()).value,
                        index = index
                    )
                }
            }
        }
    }
    if (showPeriodPicker) {
        PeriodPickerDialog(periods, selectedPeriodId, onDismiss = { showPeriodPicker = false }, onSelect = { selectedPeriodId = it.id; showPeriodPicker = false })
    }
}

@Composable
private fun ExerciseEvolutionCard(exercise: ExerciseEntity, history: List<com.lamy.gymapp.data.SessionSetEntity>, index: Int) {
    val cardColor = if (index % 2 == 0) Color.White else Color(0xFFEAF2E8)
    val sessionLoads = peakLoadsBySession(history).takeLast(8)
    val scaleMax = sessionLoads.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val lastLoad = sessionLoads.lastOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (index % 2 == 0) Color(0xFFD5E0D5) else Color(0xFFC8D9C5))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, color = Color(0xFF263A2B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(exercise.muscleGroup, color = Color(0xFF657568), style = MaterialTheme.typography.bodySmall)
                }
                if (lastLoad != null) {
                    Surface(color = SoftGreen, shape = RoundedCornerShape(10.dp)) {
                        Text("${formatLoad(lastLoad)} kg", color = Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (sessionLoads.isEmpty()) "HISTÓRICO DE CARGA · kg" else "MAIOR CARGA POR SESSÃO · kg · últimas ${sessionLoads.size}",
                color = Color(0xFF657568),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (sessionLoads.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Ainda sem cargas registradas neste período", color = Color(0xFF71877E), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(112.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    sessionLoads.forEachIndexed { barIndex, load ->
                        val height = (load / scaleMax * 66.0).toInt().coerceAtLeast(10)
                        Column(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(formatLoad(load), color = Color(0xFF425D46), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Spacer(
                                Modifier.width(22.dp).height(height.dp).background(
                                    if (barIndex == sessionLoads.lastIndex) Green else Color(0xFF8FB595),
                                    RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${barIndex + 1}", color = Color(0xFF71877E), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatLoad(load: Double): String = if (load % 1.0 == 0.0) load.toInt().toString() else load.toString()

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar") }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkoutCarouselCard(workout: WorkoutEntity, onDoubleTap: () -> Unit) {
    Card(
        modifier = Modifier.width(210.dp).pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onDoubleTap() })
        },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("TREINO ${workout.sortOrder}", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(workout.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(workout.subtitle, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall, minLines = 2)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selecionar", color = Green, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Green)
            }
        }
    }
}

@Composable
private fun WorkoutScreen(
    viewModel: GymViewModel,
    workoutId: String,
    sessionId: String?,
    workout: WorkoutEntity?,
    exercises: List<WorkoutExerciseRow>,
    onBack: () -> Unit,
    onStartSession: () -> Unit,
    onFinishSession: () -> Unit
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(exercises) { exercises.forEach { expanded[it.exerciseId] = true } }
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar") }
                Column(Modifier.weight(1f)) {
                    Text("TREINO ${workout?.sortOrder ?: ""}", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(workout?.title ?: "Treino", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${exercises.size} exercícios", color = Color(0xFF60786D), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = if (sessionId == null) onStartSession else onFinishSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(if (sessionId == null) "Iniciar treino" else "Finalizar treino", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text(workout?.subtitle.orEmpty(), color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(exercises.size) { index ->
                    val item = exercises[index]
                    val loadProfile = viewModel.loadProfile(item.exerciseId)
                        .collectAsStateWithLifecycle(initialValue = emptyList()).value
                    ExerciseCard(item, loadProfile, expanded[item.exerciseId] == true, onLoadChanged = { setIndex, value ->
                        viewModel.saveLoad(item.exerciseId, setIndex, value)
                    }, onSetChanged = { setIndex, loadKg, completed, increaseMarked ->
                        sessionId?.let { viewModel.saveSessionSet(it, item.exerciseId, setIndex, loadKg, completed, increaseMarked) }
                    }) {
                        expanded[item.exerciseId] = !(expanded[item.exerciseId] ?: true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    item: WorkoutExerciseRow,
    loadProfile: List<com.lamy.gymapp.data.ExerciseLoadProfileEntity>,
    isExpanded: Boolean,
    onLoadChanged: (Int, Double) -> Unit,
    onSetChanged: (Int, Double?, Boolean, Boolean) -> Unit,
    onToggle: () -> Unit
) {
    var restRemaining by remember(item.exerciseId) { mutableIntStateOf(0) }
    LaunchedEffect(restRemaining) {
        if (restRemaining > 0) {
            delay(1000)
            restRemaining -= 1
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5E0D5))
    ) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Color(0xFFE4EEE4)).clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF263A2B))
                    Text("${item.muscleGroup} · descanso ${item.restSeconds}s", color = Color(0xFF657568), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = { if (item.restSeconds > 0) restRemaining = item.restSeconds },
                    enabled = item.restSeconds > 0
                ) {
                    Text(
                        text = if (restRemaining > 0) "⏱ ${restRemaining}s" else "⏱ ${item.restSeconds}s",
                        color = if (restRemaining > 0) Color(0xFFB87519) else Green,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Abrir ou fechar", tint = Green)
            }
            if (isExpanded) {
                repeat(item.setCount) { setIndex ->
                    val preset = loadProfile.firstOrNull { it.setIndex == setIndex + 1 }?.lastUsedLoadKg
                    val planned = item.plannedLoadsCsv.split(",").getOrNull(setIndex)?.trim()?.toDoubleOrNull()
                    val effectivePreset = preset ?: planned
                    SetRow(
                        number = setIndex + 1,
                        reps = item.plannedReps,
                        load = effectivePreset?.let { if (it % 1.0 == 0.0) "${it.toInt()} kg" else "$it kg" }.orEmpty(),
                        completed = false,
                        onLoadChanged = { value -> value.toDoubleOrNull()?.let { onLoadChanged(setIndex + 1, it) } },
                        rowColor = if (setIndex % 2 == 0) Color(0xFFFBFCFA) else Color(0xFFEDF3EB),
                        onSetChanged = { loadKg, completed, increaseMarked -> onSetChanged(setIndex + 1, loadKg, completed, increaseMarked) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    number: Int,
    reps: String,
    load: String,
    completed: Boolean,
    onLoadChanged: (String) -> Unit,
    rowColor: Color,
    onSetChanged: (Double?, Boolean, Boolean) -> Unit
) {
    var enteredLoad by remember(load) { mutableStateOf(load) }
    var isCompleted by remember { mutableStateOf(completed) }
    var increaseMarked by remember { mutableStateOf(false) }
    fun persist() = onSetChanged(enteredLoad.removeSuffix(" kg").trim().toDoubleOrNull(), isCompleted, increaseMarked)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(rowColor).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Surface(modifier = Modifier.width(28.dp).height(28.dp), color = SoftGreen, shape = RoundedCornerShape(9.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("$number", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
        }
        Text(reps, modifier = Modifier.weight(1f).padding(start = 10.dp), color = Color(0xFF657568), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = enteredLoad,
            onValueChange = { enteredLoad = it; onLoadChanged(it.removeSuffix(" kg").trim()) },
            modifier = Modifier.width(110.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall,
            shape = RoundedCornerShape(11.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF47714D),
                unfocusedBorderColor = Color(0xFFB9C9BA),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Box(Modifier.width(1.dp).height(34.dp).background(Color(0xFFD5E0D5)))
        Spacer(Modifier.width(10.dp))
        Surface(
            modifier = Modifier.width(39.dp).height(39.dp).clickable { isCompleted = !isCompleted; persist() },
            color = if (isCompleted) SoftGreen else Color.White,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCompleted) Green else Color(0xFFC5D3C6))
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, contentDescription = "Marcar série", tint = if (isCompleted) Green else Color(0xFF9DB2A9)) }
        }
        Spacer(Modifier.width(7.dp))
        Surface(
            modifier = Modifier.width(39.dp).height(39.dp).clickable { increaseMarked = !increaseMarked; isCompleted = true; persist() },
            color = if (increaseMarked) SoftGreen else Color.White,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (increaseMarked) Green else Color(0xFFC5D3C6))
        ) {
            Box(contentAlignment = Alignment.Center) { Text("+", color = if (increaseMarked) Green else Color(0xFF9DB2A9), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
