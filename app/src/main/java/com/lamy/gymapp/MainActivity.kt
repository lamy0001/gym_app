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
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.lamy.gymapp.data.GymDatabase
import com.lamy.gymapp.data.WorkoutEntity
import com.lamy.gymapp.data.ExerciseEntity
import com.lamy.gymapp.data.WorkoutExerciseRow
import com.lamy.gymapp.data.seedIfEmpty
import com.lamy.gymapp.data.ensureCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Green = Color(0xFF19B987)
private val SoftGreen = Color(0xFFD9F6E9)
private val AppBackground = Color(0xFFF4F7F5)

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

class GymViewModel(private val database: GymDatabase) : ViewModel() {
    val workouts: Flow<List<WorkoutEntity>> = database.dao().observeWorkouts()
    val catalog: Flow<List<ExerciseEntity>> = database.dao().observeExercises()
    val completedSessionCount: Flow<Int> = database.dao().observeCompletedSessionCount()
    val completedSessions: Flow<List<com.lamy.gymapp.data.WorkoutSessionEntity>> = database.dao().observeCompletedSessions()
    val settings: Flow<List<com.lamy.gymapp.data.AppSettingEntity>> = database.dao().observeSettings()
    private val _selectedWorkoutId = MutableStateFlow<String?>(null)
    val selectedWorkoutId: StateFlow<String?> = _selectedWorkoutId
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId

    init { viewModelScope.launch { database.dao().seedIfEmpty(); database.dao().ensureCatalog() } }

    fun selectWorkout(id: String) { _selectedWorkoutId.value = id }

    fun startSession(workoutId: String) {
        val id = UUID.randomUUID().toString()
        _activeSessionId.value = id
        viewModelScope.launch { database.dao().insertSession(com.lamy.gymapp.data.WorkoutSessionEntity(id, workoutId, System.currentTimeMillis())) }
    }

    fun finishSession() {
        _activeSessionId.value?.let { id -> viewModelScope.launch { database.dao().finishSession(id, System.currentTimeMillis()) } }
    }

    fun saveSessionSet(sessionId: String, exerciseId: String, setIndex: Int, loadKg: Double?, completed: Boolean, increaseMarked: Boolean) {
        viewModelScope.launch {
            database.dao().saveSessionSet(com.lamy.gymapp.data.SessionSetEntity("$sessionId-$exerciseId-$setIndex", sessionId, exerciseId, setIndex, loadKg = loadKg, completed = completed, increaseMarked = increaseMarked))
            if (loadKg != null) saveLoad(exerciseId, setIndex, loadKg)
        }
    }

    fun deleteWorkout(workoutId: String) {
        viewModelScope.launch { database.dao().deleteWorkoutExercises(workoutId); database.dao().deleteWorkout(workoutId) }
    }

    fun updateWorkoutExercise(workoutId: String, exerciseId: String, restSeconds: Int, setCount: Int) {
        viewModelScope.launch { database.dao().updateWorkoutExercise(workoutId, exerciseId, restSeconds, setCount) }
    }

    fun exercises(workoutId: String): Flow<List<WorkoutExerciseRow>> =
        database.dao().observeWorkoutExercises(workoutId)

    fun loadProfile(exerciseId: String) = database.dao().observeLoadProfile(exerciseId)

    fun exerciseHistory(exerciseId: String) = database.dao().observeExerciseHistory(exerciseId)

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
            val exercises = dao.allExercises(); val workouts = dao.allWorkouts(); val links = dao.allWorkoutExercises(); val profiles = dao.allLoadProfiles(); val sessions = dao.allSessions(); val sessionSets = dao.allSessionSets(); val settings = dao.allSettings()
            root.put("exercises", array { exercises.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("muscleGroup", it.muscleGroup)) } })
            root.put("workouts", array { workouts.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("subtitle", it.subtitle).put("sortOrder", it.sortOrder)) } })
            root.put("workoutExercises", array { links.forEach { put(JSONObject().put("workoutId", it.workoutId).put("exerciseId", it.exerciseId).put("sortOrder", it.sortOrder).put("restSeconds", it.restSeconds).put("plannedReps", it.plannedReps).put("setCount", it.setCount).put("plannedLoadsCsv", it.plannedLoadsCsv)) } })
            root.put("loadProfiles", array { profiles.forEach { put(JSONObject().put("exerciseId", it.exerciseId).put("setIndex", it.setIndex).put("lastUsedLoadKg", it.lastUsedLoadKg)) } })
            root.put("sessions", array { sessions.forEach { put(JSONObject().put("id", it.id).put("workoutId", it.workoutId).put("startedAt", it.startedAt).put("finishedAt", it.finishedAt ?: JSONObject.NULL).put("completed", it.completed)) } })
            root.put("sessionSets", array { sessionSets.forEach { put(JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("exerciseId", it.exerciseId).put("setIndex", it.setIndex).put("reps", it.reps ?: JSONObject.NULL).put("loadKg", it.loadKg ?: JSONObject.NULL).put("completed", it.completed).put("increaseMarked", it.increaseMarked)) } })
            root.put("settings", array { settings.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) } })
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
                val sessions = (0 until root.getJSONArray("sessions").length()).map { val o = root.getJSONArray("sessions").getJSONObject(it); com.lamy.gymapp.data.WorkoutSessionEntity(o.getString("id"), o.getString("workoutId"), o.getLong("startedAt"), if (o.isNull("finishedAt")) null else o.getLong("finishedAt"), o.getBoolean("completed")) }
                val sessionSets = (0 until root.getJSONArray("sessionSets").length()).map { val o = root.getJSONArray("sessionSets").getJSONObject(it); com.lamy.gymapp.data.SessionSetEntity(o.getString("id"), o.getString("sessionId"), o.getString("exerciseId"), o.getInt("setIndex"), if (o.isNull("reps")) null else o.getInt("reps"), if (o.isNull("loadKg")) null else o.getDouble("loadKg"), o.getBoolean("completed"), o.getBoolean("increaseMarked")) }
                val settings = (0 until root.getJSONArray("settings").length()).map { val o = root.getJSONArray("settings").getJSONObject(it); com.lamy.gymapp.data.AppSettingEntity(o.getString("key"), o.getString("value")) }
                dao.insertExercises(exercises); dao.insertWorkouts(workouts); dao.insertWorkoutExercises(links); dao.saveLoadProfile(profiles)
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
    val selectedId by viewModel.selectedWorkoutId.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf("home") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
            if (screen == "workout" && selectedId != null) {
                WorkoutScreen(
                    viewModel = viewModel,
                    workoutId = selectedId!!,
                    sessionId = viewModel.activeSessionId.collectAsStateWithLifecycle().value,
                    workout = workouts.firstOrNull { it.id == selectedId },
                    exercises = viewModel.exercises(selectedId!!).collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    onBack = { viewModel.finishSession(); screen = "home" }
                )
            } else if (screen == "settings") {
                SettingsScreen(viewModel = viewModel, onBack = { screen = "home" }, onEditWorkouts = { screen = "workouts" })
            } else if (screen == "workouts") {
                WorkoutsScreen(workouts = workouts, onBack = { screen = "home" }, onOpenWorkout = { id -> viewModel.selectWorkout(id); viewModel.startSession(id); screen = "workout" }, onEditWorkout = { id -> viewModel.selectWorkout(id); screen = "edit" }, onDeleteWorkout = viewModel::deleteWorkout)
            } else if (screen == "edit" && selectedId != null) {
                EditWorkoutScreen(workout = workouts.firstOrNull { it.id == selectedId }, exercises = viewModel.exercises(selectedId!!).collectAsStateWithLifecycle(initialValue = emptyList()).value, onBack = { screen = "workouts" }, onSave = { item, rest, sets -> viewModel.updateWorkoutExercise(selectedId!!, item.exerciseId, rest, sets) })
            } else if (screen == "history") {
                HistoryScreen(
                    viewModel = viewModel,
                    catalog = viewModel.catalog.collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    onBack = { screen = "home" },
                    completedSessions = viewModel.completedSessionCount.collectAsStateWithLifecycle(initialValue = 0).value,
                    sessions = viewModel.completedSessions.collectAsStateWithLifecycle(initialValue = emptyList()).value
                )
            } else {
                HomeScreen(
                    workouts = workouts,
                    onOpenWorkout = { id -> viewModel.selectWorkout(id); viewModel.startSession(id); screen = "workout" },
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
            Text("Treinos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                workouts.forEach { workout ->
                    WorkoutCarouselCard(workout) { onOpenWorkout(workout.id) }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Menu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            MenuRow("⌂", "Início", "Tela principal", {})
            MenuRow("▣", "Meus treinos", "Ver e iniciar todos os treinos", onWorkouts)
            MenuRow("◷", "Histórico", "Frequência e evolução de cargas", onHistory)
            MenuRow("⚙", "Configurações", "Lembretes, dias e preferências", onSettings)
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
private fun WorkoutsScreen(workouts: List<WorkoutEntity>, onBack: () -> Unit, onOpenWorkout: (String) -> Unit, onEditWorkout: (String) -> Unit, onDeleteWorkout: (String) -> Unit) {
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    if (workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Excluir treino?") },
            text = { Text("O treino ${workoutToDelete!!.sortOrder} · ${workoutToDelete!!.title} será removido. O histórico de sessões será preservado.") },
            confirmButton = { TextButton(onClick = { onDeleteWorkout(workoutToDelete!!.id); workoutToDelete = null }) { Text("Excluir", color = Color(0xFFB54D49)) } },
            dismissButton = { TextButton(onClick = { workoutToDelete = null }) { Text("Cancelar") } }
        )
    }
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Header("Meus treinos", "Todos os treinos cadastrados", onBack)
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
}

@Composable
private fun EditWorkoutScreen(
    workout: WorkoutEntity?,
    exercises: List<WorkoutExerciseRow>,
    onBack: () -> Unit,
    onSave: (WorkoutExerciseRow, Int, Int) -> Unit
) {
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
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(exercises.size) { index ->
                    val item = exercises[index]
                    var restText by remember(item.exerciseId, item.restSeconds) { mutableStateOf(item.restSeconds.toString()) }
                    var setsText by remember(item.exerciseId, item.setCount) { mutableStateOf(item.setCount.toString()) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.muscleGroup, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
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
                                    onSave(item, rest, sets)
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
}

@Composable
private fun HistoryScreen(viewModel: GymViewModel, catalog: List<ExerciseEntity>, onBack: () -> Unit, completedSessions: Int, sessions: List<com.lamy.gymapp.data.WorkoutSessionEntity>) {
    var selectedExerciseId by rememberSaveable { mutableStateOf("supinated-pulldown") }
    val loadHistory = viewModel.exerciseHistory(selectedExerciseId).collectAsStateWithLifecycle(initialValue = emptyList()).value
    val today = java.time.LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val scheduledDays = sessions.mapNotNull { it.finishedAt?.let { timestamp -> java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } }.filter { it in monday..today && it.dayOfWeek.value <= 5 }.distinct().size
    val selectedName = catalog.firstOrNull { it.id == selectedExerciseId }?.name ?: "Selecione um exercício"
    Scaffold(containerColor = AppBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Header("Histórico", "Frequência e evolução", onBack)
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Frequência nos dias programados", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("$scheduledDays de 5 dias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Segunda a sexta · fins de semana não quebram a sequência", color = Color(0xFF527468), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Evolução por exercício", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                catalog.filter { it.id != "walk" }.forEach { exercise ->
                    TextButton(onClick = { selectedExerciseId = exercise.id }) {
                        Text(exercise.name, color = if (exercise.id == selectedExerciseId) Green else Color(0xFF60786D), fontWeight = if (exercise.id == selectedExerciseId) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(selectedName, fontWeight = FontWeight.Bold)
                    Text("Carga utilizada por sessão", color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    val values = loadHistory.mapNotNull { it.loadKg }.takeLast(8)
                    val maxLoad = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        if (values.isEmpty()) {
                            Text("Conclua séries para ver sua evolução", color = Color(0xFF71877E), style = MaterialTheme.typography.bodySmall)
                        }
                        values.forEachIndexed { index, value ->
                            val height = (value / maxLoad * 92.0).toInt().coerceAtLeast(12)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Text("${if (value % 1.0 == 0.0) value.toInt() else value} kg", color = Green, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(4.dp))
                                Spacer(Modifier.width(30.dp).height(height.dp).background(if (index == 4) Green else Color(0xFF63CDA5), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                                Spacer(Modifier.height(4.dp))
                                Text("${index + 1}", color = Color(0xFF71877E), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HistoryMetric("Treinos concluídos", completedSessions.toString())
            HistoryMetric("Dias programados nesta semana", "$scheduledDays de 5")
            HistoryMetric("Exercício selecionado", selectedName)
        }
    }
}

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
private fun HistoryMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
    onBack: () -> Unit
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
                }
                Text("${exercises.size} exercícios", color = Color(0xFF60786D), style = MaterialTheme.typography.labelSmall)
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            Text(item.muscleGroup, color = Color(0xFF6E857A), style = MaterialTheme.typography.bodySmall)
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                repeat(item.setCount) { setIndex ->
                    val preset = loadProfile.firstOrNull { it.setIndex == setIndex + 1 }?.lastUsedLoadKg
                    SetRow(
                        number = setIndex + 1,
                        reps = item.plannedReps,
                        load = preset?.let { if (it % 1.0 == 0.0) "${it.toInt()} kg" else "$it kg" }.orEmpty(),
                        completed = false,
                        onLoadChanged = { value -> value.toDoubleOrNull()?.let { onLoadChanged(setIndex + 1, it) } }
                        , onSetChanged = { loadKg, completed, increaseMarked -> onSetChanged(setIndex + 1, loadKg, completed, increaseMarked) }
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
    onSetChanged: (Double?, Boolean, Boolean) -> Unit
) {
    var enteredLoad by remember(load) { mutableStateOf(load) }
    var isCompleted by remember { mutableStateOf(completed) }
    var increaseMarked by remember { mutableStateOf(false) }
    fun persist() = onSetChanged(enteredLoad.removeSuffix(" kg").trim().toDoubleOrNull(), isCompleted, increaseMarked)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$number", modifier = Modifier.width(32.dp), color = Color(0xFF60786D), style = MaterialTheme.typography.bodySmall)
        Text(reps, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = enteredLoad,
            onValueChange = { enteredLoad = it; onLoadChanged(it.removeSuffix(" kg").trim()) },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall
        )
        Surface(
            modifier = Modifier.width(34.dp).height(34.dp).clickable { isCompleted = !isCompleted; persist() },
            color = if (isCompleted) SoftGreen else Color.White,
            shape = RoundedCornerShape(9.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCompleted) Green else Color(0xFFD4E0DA))
        ) {
            Icon(Icons.Default.Check, contentDescription = "Marcar série", tint = if (isCompleted) Green else Color(0xFF9DB2A9), modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            modifier = Modifier.width(34.dp).height(34.dp).clickable { increaseMarked = !increaseMarked; isCompleted = true; persist() },
            color = if (increaseMarked) SoftGreen else Color.White,
            shape = RoundedCornerShape(9.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (increaseMarked) Green else Color(0xFFD4E0DA))
        ) {
            Text("+", color = if (increaseMarked) Green else Color(0xFF9DB2A9), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
        }
    }
}
