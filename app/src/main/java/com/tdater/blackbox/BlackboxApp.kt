package com.tdater.blackbox

import android.app.Application
import android.content.Context
import com.tdater.blackbox.data.AttachmentStore
import com.tdater.blackbox.data.BlackboxRepository
import com.tdater.blackbox.data.db.BlackboxDatabase
import java.io.File

class BlackboxApp : Application() {
    val database: BlackboxDatabase by lazy { BlackboxDatabase.build(this) }
    val repository: BlackboxRepository by lazy {
        BlackboxRepository(database, AttachmentStore(File(filesDir, "attachments")))
    }
    val settings: AppSettings by lazy { AppSettings(this) }
}

/** Small device-local preferences. Contains no secrets. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Global AI switch. Off by default; no provider ships in this build regardless. */
    var aiEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", false)
        set(v) { prefs.edit().putBoolean("ai_enabled", v).apply() }

    var lastProjectId: String?
        get() = prefs.getString("last_project", null)
        set(v) { prefs.edit().putString("last_project", v).apply() }
}
