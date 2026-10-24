package com.tdater.blackbox

import android.app.Application
import android.content.Context
import com.tdater.blackbox.ai.ModelStore
import com.tdater.blackbox.data.AttachmentStore
import com.tdater.blackbox.data.BlackboxRepository
import com.tdater.blackbox.data.db.BlackboxDatabase
import com.tdater.blackbox.data.db.DbEncryption
import com.tdater.blackbox.ui.components.Wording
import com.tdater.blackbox.ui.theme.ThemeChoice
import java.io.File

class BlackboxApp : Application() {
    val database: BlackboxDatabase by lazy { BlackboxDatabase.build(this, DbEncryption.factory(this, BlackboxDatabase.NAME)) }
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

    var theme: ThemeChoice
        get() = prefs.getString("theme", null)?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.SYSTEM
        set(v) { prefs.edit().putString("theme", v.name).apply() }

    var wording: Wording
        get() = prefs.getString("wording", null)?.let { runCatching { Wording.valueOf(it) }.getOrNull() } ?: Wording.EVERYDAY
        set(v) { prefs.edit().putString("wording", v.name).apply() }

    /** Ask for fingerprint / screen lock when opening BLACKBOX. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(v) { prefs.edit().putBoolean("app_lock", v).apply() }

    /** Blank the app in the recent-apps switcher and block screenshots. */
    var hideInRecents: Boolean
        get() = prefs.getBoolean("hide_recents", false)
        set(v) { prefs.edit().putBoolean("hide_recents", v).apply() }

    /** Folder (a Storage Access Framework tree URI) that backups are written to. */
    var backupTree: String?
        get() = prefs.getString("backup_tree", null)
        set(v) { prefs.edit().putString("backup_tree", v).apply() }

    var autoBackup: Boolean
        get() = prefs.getBoolean("auto_backup", true)
        set(v) { prefs.edit().putBoolean("auto_backup", v).apply() }

    var lastBackupAt: Long
        get() = prefs.getLong("last_backup", 0L)
        set(v) { prefs.edit().putLong("last_backup", v).apply() }
}
