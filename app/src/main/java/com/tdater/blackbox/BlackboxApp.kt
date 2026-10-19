package com.tdater.blackbox

import android.app.Application
import android.content.Context
import com.tdater.blackbox.ai.ModelStore
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
    val models: ModelStore by lazy { ModelStore(File(filesDir, "models")) }
}

/** Small device-local preferences. Contains no secrets. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Global AI switch. Off by default; the only provider is an on-device model the user imports. */
    var aiEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", false)
        set(v) { prefs.edit().putBoolean("ai_enabled", v).apply() }

    var lastProjectId: String?
        get() = prefs.getString("last_project", null)
        set(v) { prefs.edit().putString("last_project", v).apply() }
}
