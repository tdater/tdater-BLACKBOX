package com.tdater.blackbox.system

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tdater.blackbox.AppSettings
import com.tdater.blackbox.core.archive.ArchiveException
import com.tdater.blackbox.core.procedures.ProcedureLibrary
import com.tdater.blackbox.data.BlackboxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backups to a folder the user picks (phone storage, SD card, or a synced folder such as Syncthing or a cloud
 * provider's folder). BLACKBOX only writes files there; any syncing is done by whatever app owns that folder.
 * One archive per project, overwritten in place, plus the saved checklists.
 */
class Backup(private val context: Context, private val repo: BlackboxRepository, private val settings: AppSettings) {
    private val lock = Mutex()

    data class Result(val projects: Int, val bytes: Long, val folderName: String)
    data class Found(val name: String, val uri: Uri, val bytes: Long, val modified: Long)

    private fun folder(): DocumentFile {
        val tree = settings.backupTree ?: throw IllegalStateException("Pick a backup folder first.")
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(tree))
        if (dir == null || !dir.canWrite()) throw IllegalStateException("BLACKBOX can no longer write to the backup folder. Pick it again.")
        return dir
    }

    private fun fileName(title: String, id: String) =
        "BLACKBOX - " + title.replace(Regex("[^A-Za-z0-9 _-]+"), "").trim().take(40).ifEmpty { "project" } + " - ${id.take(8)}.zip"

    /** True when something changed since the last backup (or a day has passed). */
    suspend fun isDue(): Boolean {
        if (settings.backupTree == null || !settings.autoBackup) return false
        val last = settings.lastBackupAt
        if (System.currentTimeMillis() - last > 24 * 3600_000L) return true
        return repo.allProjects().any { it.updatedAt > last }
    }

    suspend fun backUpNow(): Result = lock.withLock {
        withContext(Dispatchers.IO) {
            val dir = folder()
            val projects = repo.allProjects().filter { !it.isSample }
            var bytes = 0L
            for (p in projects) {
                val name = fileName(p.title, p.id)
                // Stale copies of the same project under an old title are replaced, not piled up.
                dir.listFiles().filter { it.name?.endsWith(" - ${p.id.take(8)}.zip") == true && it.name != name }.forEach { it.delete() }
                val f = dir.findFile(name) ?: dir.createFile("application/zip", name.removeSuffix(".zip"))
                    ?: throw IllegalStateException("Could not create $name in the backup folder.")
                context.contentResolver.openOutputStream(f.uri, "wt")?.use { repo.exportArchive(p.id, it) }
                    ?: throw IllegalStateException("Could not write $name.")
                bytes += f.length()
            }
            val procs = repo.allProcedures()
            val pf = dir.findFile(PROCEDURES) ?: dir.createFile("application/json", PROCEDURES.removeSuffix(".json"))
            pf?.let { f -> context.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(ProcedureLibrary.encode(procs).toByteArray()) } }
            settings.lastBackupAt = System.currentTimeMillis()
            Result(projects.size, bytes, dir.name ?: "backup folder")
        }
    }

    suspend fun list(): List<Found> = withContext(Dispatchers.IO) {
        folder().listFiles().filter { it.isFile && it.name?.endsWith(".zip") == true }
            .map { Found(it.name!!, it.uri, it.length(), it.lastModified()) }.sortedBy { it.name }
    }

    /** Restores one archive as a NEW project (imports never overwrite), and merges checklists if present. */
    suspend fun restore(found: Found): String = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "restore-${System.currentTimeMillis()}")
        try {
            val input = context.contentResolver.openInputStream(found.uri) ?: throw ArchiveException("Could not open ${found.name}.")
            val (pid, _) = input.use { repo.importArchive(it, stage) }
            restoreProcedures()
            pid
        } finally {
            stage.deleteRecursively()
        }
    }

    private suspend fun restoreProcedures() {
        val f = runCatching { folder().findFile(PROCEDURES) }.getOrNull() ?: return
        val text = context.contentResolver.openInputStream(f.uri)?.use { it.readBytes().decodeToString() } ?: return
        runCatching { ProcedureLibrary.decode(text) }.getOrNull()?.let { repo.importProcedures(it) }
    }

    companion object {
        const val PROCEDURES = "BLACKBOX - checklists.json"
    }
}
