package com.tdater.blackbox.ai

import android.content.ContentResolver
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File

/**
 * Holds at most one on-device language model file, copied into app storage by the user.
 * BLACKBOX has no network permission, so the only way in is an explicit file import.
 */
class ModelStore(private val dir: File) {
    data class Info(val displayName: String, val bytes: Long, val file: File)

    private val modelFile get() = File(dir, "model.litertlm")
    private val nameFile get() = File(dir, "model.name")

    fun current(): Info? = modelFile.takeIf { it.isFile && it.length() > 0 }?.let {
        Info(runCatching { nameFile.readText() }.getOrNull()?.ifBlank { null } ?: it.name, it.length(), it)
    }

    fun import(resolver: ContentResolver, uri: Uri): Info {
        var name = "model.litertlm"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { i -> size = c.getLong(i) }
            }
        }
        if (!name.endsWith(".litertlm", ignoreCase = true) && !name.endsWith(".task", ignoreCase = true)) {
            throw IllegalArgumentException("\"$name\" is not a LiteRT-LM model (.litertlm or .task).")
        }
        dir.mkdirs()
        val free = StatFs(dir.path).availableBytes
        size?.let { if (it + RESERVE_BYTES > free) throw IllegalStateException("Not enough storage: model is ${it / MB} MB, ${free / MB} MB free.") }
        val tmp = File(dir, "model.part")
        try {
            val input = resolver.openInputStream(uri) ?: throw IllegalStateException("The file could not be opened.")
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var total = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        if (total % (64 * MB) < n && StatFs(dir.path).availableBytes < RESERVE_BYTES)
                            throw IllegalStateException("Device storage is nearly full; model import stopped.")
                    }
                }
            }
            if (tmp.length() < MIN_MODEL_BYTES) throw IllegalArgumentException("\"$name\" is too small to be a language model.")
            modelFile.delete()
            if (!tmp.renameTo(modelFile)) throw IllegalStateException("Could not finalize the model file.")
            nameFile.writeText(name)
        } finally {
            tmp.delete()
        }
        return current()!!
    }

    fun remove() {
        modelFile.delete()
        nameFile.delete()
    }

    companion object {
        private const val MB = 1024L * 1024
        private const val RESERVE_BYTES = 512L * MB
        private const val MIN_MODEL_BYTES = 10L * MB
    }
}
