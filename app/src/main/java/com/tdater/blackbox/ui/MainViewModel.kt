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
import com.tdater.blackbox.core.model.EvidenceKind
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

data class RecordingTarget(val projectId: String, val investigationId: String?, val runId: String?)

/**
 * Activity-scoped state that must survive configuration changes (fold/unfold, rotation):
 * user-visible messages, the busy flag and any in-progress audio recording.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val blackbox = app as BlackboxApp
    val repo = blackbox.repository
    val settings = blackbox.settings

    var message by mutableStateOf<String?>(null)
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

    override fun onCleared() {
        // Activity finishing for good: save whatever was recorded rather than dropping it.
        if (recorder != null) stopRecording(save = true)
        super.onCleared()
    }
}
