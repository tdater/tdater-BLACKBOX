package com.tdater.blackbox.ui

import android.app.Application
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tdater.blackbox.BlackboxApp
import com.tdater.blackbox.ai.OnDeviceProvider
import com.tdater.blackbox.core.ai.AiGateway
import com.tdater.blackbox.core.ai.AiPrompt
import com.tdater.blackbox.core.ai.AiResult
import com.tdater.blackbox.core.ai.ContextItem
import com.tdater.blackbox.core.ai.ContextPacket
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.Project
import com.tdater.blackbox.system.Backup
import com.tdater.blackbox.system.Reminders
import com.tdater.blackbox.ui.components.Wording
import com.tdater.blackbox.ui.theme.ThemeChoice
import kotlinx.coroutines.Job
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

data class RecordingTarget(val projectId: String, val investigationId: String?, val runId: String?)

/** Result of one on-device AI ask, kept until the user leaves it or asks again. */
data class AiOutcome(val investigationId: String, val task: AiPrompt.Task, val result: AiResult)

/**
 * Activity-scoped state that must survive configuration changes (fold/unfold, rotation):
 * user-visible messages, the busy flag and any in-progress audio recording.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val blackbox = app as BlackboxApp
    val repo = blackbox.repository
    val settings = blackbox.settings
    val models = blackbox.models

    var message by mutableStateOf<String?>(null)

    // ---- Look and wording (state here so changes apply immediately everywhere) ----
    var theme by mutableStateOf(settings.theme)
        private set
    var wording by mutableStateOf(settings.wording)
        private set
    fun updateTheme(t: ThemeChoice) { theme = t; settings.theme = t }
    fun updateWording(w: Wording) { wording = w; settings.wording = w }

    /** A capture mode requested from the widget or quick-settings tile, waiting for navigation. */
    /** App lock: starts locked on a cold start when enabled; the activity re-locks after time in the background. */
    var locked by mutableStateOf(settings.appLock)
    var hiddenAt = 0L
    var pendingQuick by mutableStateOf<String?>(null)
    /** A project/investigation to open (from a reminder notification). */
    var pendingOpen by mutableStateOf<Pair<String, String?>?>(null)

    val reminders = Reminders(app)
    val backup = Backup(app, repo, settings)

    /** Saves (or clears, with null) a follow-up and schedules the matching local notification. */
    fun setFollowUp(inv: Investigation, at: Long?, note: String) = launch("Reminder not saved") {
        repo.setFollowUp(inv.id, at, note)
        if (at == null) reminders.cancel(inv.id) else reminders.schedule(inv.copy(followUpAt = at, followUpNote = note))
        message = if (at == null) "Reminder cleared." else "Reminder set."
    }

    /** Called when the app goes to the background: back up if a folder is set and something changed. */
    fun backupIfDue() {
        saveScope.launch {
            try { if (backup.isDue()) backup.backUpNow() } catch (_: Exception) { /* shown next time in Settings */ }
        }
    }
    /** Shared-in content awaiting a project choice; survives rotation/fold. */
    var pendingShare by mutableStateOf<PendingShare?>(null)

    var busy by mutableStateOf(false)
        private set

    fun launch(errorPrefix: String = "Failed", block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                message = "$errorPrefix: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    // ---- On-device AI (held here so folding, rotating or leaving the screen never restarts a slow generation) ----
    var aiRunningFor by mutableStateOf<String?>(null)
        private set
    var aiOutcome by mutableStateOf<AiOutcome?>(null)
    private var aiJob: Job? = null

    fun askAi(project: Project, investigationTitle: String, investigationId: String, task: AiPrompt.Task, items: List<ContextItem>) {
        if (aiJob?.isActive == true) return
        aiOutcome = null
        aiRunningFor = investigationId
        aiJob = viewModelScope.launch {
            try {
                val built = AiPrompt.build(task, investigationTitle, items)
                // Policy is re-read from the database at request time, not trusted from the screen.
                val current = repo.allProjects().firstOrNull { it.id == project.id }
                val model = kotlinx.coroutines.withContext(Dispatchers.IO) { models.current() }
                val gateway = AiGateway(model?.let { OnDeviceProvider(it.file, built) }, { settings.aiEnabled },
                    { pid -> current?.takeIf { it.id == pid } }, timeoutMs = AI_TIMEOUT_MS)
                aiOutcome = AiOutcome(investigationId, task, gateway.request(ContextPacket(task.name, items)))
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiOutcome = AiOutcome(investigationId, task, AiResult.Failed("Cancelled. Nothing was saved."))
                throw e
            } catch (e: Exception) {
                aiOutcome = AiOutcome(investigationId, task, AiResult.Failed("Could not run the model: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                aiRunningFor = null
            }
        }
    }

    fun cancelAi() { aiJob?.cancel() }

    // ---- Voice recording (kept here so rotation/fold never restarts or duplicates it) ----
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    var recordingTarget by mutableStateOf<RecordingTarget?>(null)
        private set
    var recordingStartedAt by mutableStateOf(0L)
        private set

    val isRecording: Boolean get() = recordingTarget != null

    /** Outlives the ViewModel so a recording stopped in onCleared is still saved. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startRecording(target: RecordingTarget) {
        if (recorder != null) return
        val ctx = getApplication<Application>()
        val file = repo.attachments.newCaptureFile(ctx.cacheDir, "m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            file.delete()
            message = "Could not start recording: ${e.message ?: e.javaClass.simpleName}"
            return
        }
        recorder = r
        recordingFile = file
        recordingTarget = target
        recordingStartedAt = SystemClock.elapsedRealtime()
    }

    /** Stops and saves. Audio is stored as-is; no transcription exists in this build. */
    fun stopRecording(save: Boolean) {
        val r = recorder ?: return
        val file = recordingFile
        val target = recordingTarget
        val seconds = (SystemClock.elapsedRealtime() - recordingStartedAt) / 1000
        recorder = null
        recordingFile = null
        recordingTarget = null
        val ok = try { r.stop(); true } catch (e: RuntimeException) { false } finally { r.release() }
        if (!save || file == null || target == null) { file?.delete(); return }
        if (!ok || file.length() == 0L) {
            file.delete()
            message = "Recording was too short or failed; nothing saved."
            return
        }
        saveScope.launch {
            message = try {
                repo.addCapturedFile(file, target.projectId, target.investigationId, target.runId, EvidenceKind.AUDIO,
                    "voice-${System.currentTimeMillis()}.m4a", "audio/mp4", "Voice note (${seconds}s, not transcribed)")
                "Voice note saved."
            } catch (e: Exception) {
                "Saving recording failed: ${e.message}"
            }
        }
    }

    companion object {
        /** CPU generation on a phone is slow; three minutes before giving up. */
        const val AI_TIMEOUT_MS = 180_000L
    }

    override fun onCleared() {
        // Activity finishing for good: save whatever was recorded rather than dropping it.
        if (recorder != null) stopRecording(save = true)
        super.onCleared()
    }
}
