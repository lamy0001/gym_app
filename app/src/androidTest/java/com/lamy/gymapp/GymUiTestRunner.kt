package com.lamy.gymapp

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.lamy.gymapp.data.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Run only against a separate application ID so tests cannot modify a user's training data. */
class GymUiTestRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = StringBuilder()
        try {
            check(targetContext.packageName.endsWith(".validation")) { "Use -PqaApplicationId=com.lamy.gymapp.validation" }
            val database = GymDatabase.create(targetContext)
            runBlocking {
                val dao = database.dao()
                dao.seedIfEmpty()
                dao.ensureCatalog()
                dao.deactivatePeriods()
                dao.insertPeriod(TrainingPeriodEntity("qa-period", "Validação QA", 0L))
                dao.insertWorkouts(listOf(WorkoutEntity("qa-workout", "Teste de interação", "Somente validação", 1)))
                dao.insertPeriodLink(WorkoutPeriodLinkEntity("qa-workout", "qa-period"))
                dao.insertExercises(listOf(ExerciseEntity("qa-exercise", "Exercício de validação", "Teste"), ExerciseEntity("qa-single", "Exercício de uma série", "Teste")))
                dao.insertWorkoutExercises(listOf(WorkoutExerciseEntity("qa-workout", "qa-exercise", 1, 30, "10–12", 2, "10,15"), WorkoutExerciseEntity("qa-workout", "qa-single", 2, 30, "12", 1, "7")))
                dao.saveSetting(AppSettingEntity("remindersEnabled", "false"))
            }
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            tapText("Meus treinos")
            tapText("Treino 1 · Teste de interação")
            val field = waitNode { it.isEditable }
            check(!field.text.toString().contains("kg")) { "kg must not be part of the editable value" }
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "12,5") }))
            waitUntil { runBlocking { database.dao().allLoadProfiles().any { it.exerciseId == "qa-exercise" && it.setIndex == 1 && it.lastUsedLoadKg == 12.5 } } }
            report.append("PASS: numeric-only editing and comma decimal persistence\n")

            tapDescription("Marcar série 1")
            waitUntil { runBlocking { database.dao().allSessionSets().any { it.exerciseId == "qa-exercise" && it.setIndex == 1 && it.completed } } }
            check(find { it.isEditable && it.text.toString() == "15" } != null) { "Card collapsed before the last series" }
            tapDescription("Marcar série 2")
            waitUntil { find { it.isEditable && it.text.toString() == "15" } == null }
            tapDescription("Abrir ou fechar Exercício de validação")
            waitUntil { find { it.contentDescription?.toString() == "Marcar série 2" } != null }
            check(checkNode("Marcar série 1").isChecked && checkNode("Marcar série 2").isChecked)
            report.append("PASS: individual checks collapse only after every series; reopening retains checks\n")

            tapDescription("Marcar série 2")
            waitUntil { !checkNode("Marcar série 2").isChecked }
            tapDescription("Marcar todas as séries de Exercício de validação")
            waitUntil { find { it.contentDescription?.toString() == "Marcar série 2" } == null }
            tapDescription("Abrir ou fechar Exercício de validação")
            waitUntil { find { it.contentDescription?.toString() == "Marcar série 2" } != null }
            check(checkNode("Marcar série 1").isChecked && checkNode("Marcar série 2").isChecked)
            tapDescription("Abrir ou fechar Exercício de validação")
            tapDescription("Marcar todas as séries de Exercício de uma série")
            waitUntil { runBlocking { database.dao().allSessionSets().any { it.exerciseId == "qa-single" && it.completed } } }
            report.append("PASS: bulk action on multi-series and single-series cards\n")

            runOnMainSync { activity.finish() }
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitNode { it.text?.toString() == "Finalizar treino" }
            check(runBlocking { database.dao().allSessionSets().filter { it.exerciseId == "qa-exercise" }.all { it.completed } })
            report.append("PASS: active session and series survive activity reopening\n")

            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            tapDescription("Configurações")
            tapDescription("Ativar lembrete do treino")
            waitUntil { runBlocking { database.dao().allSettings().any { it.key == "remindersEnabled" && it.value == "true" } } }
            tapText("Sáb")
            waitUntil { runBlocking { database.dao().allSettings().any { it.key == "trainingDays" && 6 in trainingDays(it.value) } } }
            tapText("Horário do lembrete")
            waitNode { it.packageName?.toString() == targetContext.packageName && it.className?.toString() == "android.widget.TimePicker" }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            tapDescription("Ativar lembrete do treino")
            waitUntil { runBlocking { database.dao().allSettings().any { it.key == "remindersEnabled" && it.value == "false" } } }
            report.append("PASS: settings switch and scheduled days persist; time picker opens\n")

            val manager = targetContext.getSystemService(NotificationManager::class.java)
            manager.cancelAll()
            runBlocking {
                database.dao().saveSetting(AppSettingEntity("remindersEnabled", "true"))
                database.dao().saveSetting(AppSettingEntity("trainingDays", LocalDate.now().dayOfWeek.value.toString()))
                database.dao().saveSetting(AppSettingEntity("reminderTime", "18:30"))
            }
            targetContext.sendBroadcast(Intent(targetContext, ReminderReceiver::class.java))
            waitUntil { manager.activeNotifications.any { it.id == 4107 } }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME)
            waitNode { it.packageName?.toString()?.let { name -> name != targetContext.packageName } == true }
            manager.activeNotifications.first { it.id == 4107 }.notification.contentIntent.send()
            waitNode { it.packageName?.toString() == targetContext.packageName }
            runBlocking { database.dao().saveSetting(AppSettingEntity("remindersEnabled", "false")) }
            scheduleReminder(targetContext, false, "18:30", setOf(1))
            waitUntil { manager.activeNotifications.none { it.id == 4107 } }
            targetContext.sendBroadcast(Intent(targetContext, ReminderReceiver::class.java))
            SystemClock.sleep(500)
            check(manager.activeNotifications.none { it.id == 4107 })
            report.append("PASS: reminder opens the app; disabling cancels and suppresses notifications\n")
            database.close()
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString() + "FAIL: " + error.stackTraceToString()) })
        }
    }

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { child -> walk(child)?.let { return it } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::walk)
    }

    private fun waitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        waitUntil { result = find(predicate); result != null }
        return result!!
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for UI/database state")
    }

    private fun tap(node: AccessibilityNodeInfo) {
        var target = node
        while (!target.isClickable) target = target.parent ?: error("No clickable parent for ${node.text ?: node.contentDescription}")
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun tapText(text: String) = tap(waitNode { it.text?.toString() == text })
    private fun tapDescription(description: String) = tap(waitNode { it.contentDescription?.toString() == description })
    private fun checkNode(description: String): AccessibilityNodeInfo {
        var target = waitNode { it.contentDescription?.toString() == description }
        while (!target.isCheckable) target = target.parent ?: error("Missing checkbox state for $description")
        return target
    }
}
