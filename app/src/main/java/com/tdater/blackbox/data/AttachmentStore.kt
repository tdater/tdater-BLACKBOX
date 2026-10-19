package com.tdater.blackbox.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

data class StoredAttachment(
    val relativePath: String,
    val sha256: String,
    val bytes: Long,
    val mimeType: String?,
    val displayName: String,
)

class AttachmentException(message: String) : Exception(message)

/**
 * App-private durable copies of attachments under filesDir/attachments.
 * Originals are copied once at import so later loss of the external URI grant does not matter.
 */
class AttachmentStore(private val root: File) {

    companion object {
        const val MAX_IMPORT_BYTES = 512L * 1024 * 1024
        /** Keep this much free space after any write. */
        const val RESERVE_BYTES = 64L * 1024 * 1024
        private val BLOCKED_MIME = setOf(
            "application/vnd.android.package-archive", "application/x-msdownload", "application/x-executable",
            "application/x-sharedlib", "application/x-sh", "application/x-elf",
        )
        private val BLOCKED_EXT = setOf("apk", "apks", "xapk", "exe", "dll", "so", "sh", "bat", "cmd", "dex", "jar")
    }

    init { root.mkdirs() }

    fun file(relativePath: String): File {
        val f = File(root, relativePath).canonicalFile
        if (!f.path.startsWith(root.canonicalPath + File.separator)) throw AttachmentException("Invalid attachment path")
        return f
    }

    fun exists(relativePath: String?): Boolean = relativePath != null && runCatching { file(relativePath).isFile }.getOrDefault(false)

    fun open(relativePath: String): InputStream? = runCatching { file(relativePath).takeIf { it.isFile }?.inputStream() }.getOrNull()

    fun freeBytes(): Long = root.usableSpace

    fun usedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun importFromUri(resolver: ContentResolver, uri: Uri, projectId: String, evidenceId: String): StoredAttachment {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "import"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { i -> size = c.getLong(i) }
            }
        }
        val mime = resolver.getType(uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (mime in BLOCKED_MIME || ext in BLOCKED_EXT) {
            throw AttachmentException("Executable/package files are not accepted as evidence ($name).")
        }
        size?.let { checkSpace(it) }
        val input = resolver.openInputStream(uri) ?: throw AttachmentException("The file could not be opened. Permission may have been revoked.")
        return input.use { write(it, projectId, evidenceId, name, mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)) }
    }

    /** Moves a file captured into app storage (camera/recorder) into the store. */
    fun adoptFile(source: File, projectId: String, evidenceId: String, displayName: String, mime: String?): StoredAttachment =
        source.inputStream().use { write(it, projectId, evidenceId, displayName, mime) }.also { source.delete() }

    fun write(input: InputStream, projectId: String, evidenceId: String, displayName: String, mime: String?): StoredAttachment {
        val safeName = sanitize(displayName)
        val rel = "$projectId/$evidenceId/$safeName"
        val target = file(rel)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".$safeName.part")
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) throw AttachmentException("File is larger than ${MAX_IMPORT_BYTES / (1024 * 1024)} MB.")
                    if (total % (8L * 1024 * 1024) < n && freeBytes() < RESERVE_BYTES) throw AttachmentException("Device storage is nearly full.")
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
            if (!tmp.renameTo(target)) throw IOException("Could not finalize attachment")
        } catch (e: Exception) {
            tmp.delete()
            target.parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete()
            throw if (e is AttachmentException) e else AttachmentException("Copy failed: ${e.message}")
        }
        return StoredAttachment(rel, md.digest().joinToString("") { "%02x".format(it) }, total, mime, displayName)
    }

    fun delete(relativePath: String) {
        runCatching { file(relativePath).delete(); file(relativePath).parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete() }
    }

    fun deleteProject(projectId: String) {
        runCatching { File(root, projectId).canonicalFile.takeIf { it.parentFile == root.canonicalFile }?.deleteRecursively() }
    }

    fun newCaptureFile(cacheDir: File, ext: String): File {
        val dir = File(cacheDir, "capture").apply { mkdirs() }
        return File.createTempFile("cap_", ".$ext", dir)
    }

    private fun checkSpace(needed: Long) {
        if (needed > MAX_IMPORT_BYTES) throw AttachmentException("File is larger than ${MAX_IMPORT_BYTES / (1024 * 1024)} MB.")
        if (freeBytes() - needed < RESERVE_BYTES) throw AttachmentException("Not enough free storage to import this file.")
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().trimStart('.')
        return cleaned.take(96).ifEmpty { "file" }
    }
}
