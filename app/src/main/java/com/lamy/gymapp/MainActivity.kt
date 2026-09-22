package com.lamy.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.lamy.gymapp.data.GymDatabase
import com.lamy.gymapp.data.WorkoutEntity
import com.lamy.gymapp.data.WorkoutExerciseRow
import com.lamy.gymapp.data.seedIfEmpty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Green = Color(0xFF19B987)
private val SoftGreen = Color(0xFFD9F6E9)
private val AppBackground = Color(0xFFF4F7F5)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GymViewModel> {
        GymViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GymApp(viewModel) }
    }
}

class GymViewModel(private val database: GymDatabase) : ViewModel() {
    val workouts: Flow<List<WorkoutEntity>> = database.dao().observeWorkouts()
    private val _selectedWorkoutId = MutableStateFlow<String?>(null)
    val selectedWorkoutId: StateFlow<String?> = _selectedWorkoutId

    init { viewModelScope.launch { database.dao().seedIfEmpty() } }

    fun selectWorkout(id: String) { _selectedWorkoutId.value = id }

    fun exercises(workoutId: String): Flow<List<WorkoutExerciseRow>> =
        database.dao().observeWorkoutExercises(workoutId)

    fun loadProfile(exerciseId: String) = database.dao().observeLoadProfile(exerciseId)

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
                    workout = workouts.firstOrNull { it.id == selectedId },
                    exercises = viewModel.exercises(selectedId!!).collectAsStateWithLifecycle(initialValue = emptyList()).value,
                    onBack = { screen = "home" }
                )
            } else if (screen == "settings") {
                SettingsScreen(onBack = { screen = "home" })
            } else {
                HomeScreen(
                    workouts = workouts,
                    onOpenWorkout = { id -> viewModel.selectWorkout(id); screen = "workout" },
                    onSettings = { screen = "settings" }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var remindersEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedRest by rememberSaveable { mutableIntStateOf(90) }

    Scaffold(containerColor = AppBackground) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)
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
                Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
            }
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
                    TextButton(onClick = { selectedRest = seconds }) {
                        Text("$seconds s", color = if (selectedRest == seconds) Green else Color(0xFF60786D), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsRow("Sons e vibração", "Ativo")
            SettingsRow("Editar treinos", "Abrir")
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
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
    onSettings: () -> Unit
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
    onToggle: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${item.restSeconds}s", color = Green, style = MaterialTheme.typography.labelSmall)
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Abrir ou fechar", tint = Green)
            }
            Text(item.muscleGroup, color = Color(0xFF6E857A), style = MaterialTheme.typography.bodySmall)
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                repeat(3) { setIndex ->
                    val preset = loadProfile.firstOrNull { it.setIndex == setIndex + 1 }?.lastUsedLoadKg
                    SetRow(
                        number = setIndex + 1,
                        reps = item.plannedReps,
                        load = preset?.let { if (it % 1.0 == 0.0) "${it.toInt()} kg" else "$it kg" }.orEmpty(),
                        completed = setIndex == 0,
                        onLoadChanged = { value -> value.toDoubleOrNull()?.let { onLoadChanged(setIndex + 1, it) } }
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
    onLoadChanged: (String) -> Unit
) {
    var enteredLoad by remember(load) { mutableStateOf(load) }
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
        TextButton(onClick = { }) {
            Icon(Icons.Default.Check, contentDescription = "Marcar série", tint = if (completed) Green else Color(0xFF9DB2A9))
        }
        TextButton(onClick = { }) { Text("＋", color = Green, fontWeight = FontWeight.Bold) }
    }
}
