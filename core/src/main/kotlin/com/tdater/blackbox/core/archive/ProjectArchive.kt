package com.tdater.blackbox.core.archive

import com.tdater.blackbox.core.model.ProjectBundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class ArchiveFile(val path: String, val sha256: String, val bytes: Long)

@Serializable
data class ArchiveManifest(
    val format: String = FORMAT,
    val formatVersion: Int = VERSION,
    val exportedAt: Long,
    val projectId: String,
    val projectTitle: String,
    val recordCounts: Map<String, Int>,
    val bundleSha256: String,
    val files: List<ArchiveFile>,
    /** Attachments referenced by records but absent from the store at export time. */
    val missingAttachments: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "blackbox-project-archive"
        const val VERSION = 1
    }
}

class ArchiveException(message: String) : Exception(message)

data class ArchiveLimits(
    val maxEntries: Int = 10_000,
    val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxJsonBytes: Long = 64L * 1024 * 1024,
)

data class ExportResult(val manifest: ArchiveManifest)

data class ReadArchive(
    val manifest: ArchiveManifest,
    val bundle: ProjectBundle,
    /** attachmentPath (as referenced by Evidence) -> staged file with verified checksum. */
    val attachments: Map<String, File>,
)

/**
 * Versioned zip archive: `attachments/<attachmentPath>`, `bundle.json`, `manifest.json` (written last).
 * Reading is streaming, path-validated, size-bounded and checksum-verified.
 */
object ProjectArchive {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private const val ATTACH_PREFIX = "attachments/"
    private const val BUNDLE = "bundle.json"
    private const val MANIFEST = "manifest.json"

    fun write(
        bundle: ProjectBundle,
        out: OutputStream,
        now: Long,
        openAttachment: (String) -> InputStream?,
    ): ExportResult {
        val files = mutableListOf<ArchiveFile>()
        val missing = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            val paths = bundle.evidence.mapNotNull { it.attachmentPath }.distinct()
            for (p in paths) {
                requireSafeRelative(p)
                val input = openAttachment(p)
                if (input == null) {
                    missing += p
                    continue
                }
                zip.putNextEntry(ZipEntry(ATTACH_PREFIX + p))
                val md = MessageDigest.getInstance("SHA-256")
                var total = 0L
                input.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                        zip.write(buf, 0, n)
                        total += n
                    }
                }
                zip.closeEntry()
                files += ArchiveFile(ATTACH_PREFIX + p, md.digest().toHex(), total)
            }
            val bundleBytes = json.encodeToString(ProjectBundle.serializer(), bundle).toByteArray()
            zip.putNextEntry(ZipEntry(BUNDLE))
            zip.write(bundleBytes)
            zip.closeEntry()
            val manifest = ArchiveManifest(
                exportedAt = now,
                projectId = bundle.project.id,
                projectTitle = bundle.project.title,
                recordCounts = bundle.recordCounts(),
                bundleSha256 = sha256(bundleBytes),
                files = files,
                missingAttachments = missing,
            )
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(ArchiveManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            return ExportResult(manifest)
        }
    }

    /**
     * Reads and validates an archive. Attachments are streamed into [stagingDir]; on any
     * failure the staged files are deleted and [ArchiveException] is thrown.
     */
    fun read(input: InputStream, stagingDir: File, limits: ArchiveLimits = ArchiveLimits()): ReadArchive {
        stagingDir.mkdirs()
        val staged = mutableMapOf<String, Pair<File, ArchiveFile>>()
        var bundleBytes: ByteArray? = null
        var manifestBytes: ByteArray? = null
        var entries = 0
        var totalBytes = 0L
        val seen = HashSet<String>()
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > limits.maxEntries) throw ArchiveException("Archive has too many entries (> ${limits.maxEntries}).")
                    val name = entry.name
                    if (!seen.add(name)) throw ArchiveException("Duplicate archive entry: $name")
                    if (entry.isDirectory) { validateEntryName(name.trimEnd('/')); continue }
                    validateEntryName(name)
                    when {
                        name == BUNDLE -> bundleBytes = readBounded(zip, limits.maxJsonBytes, name).also { totalBytes += it.size }
                        name == MANIFEST -> manifestBytes = readBounded(zip, limits.maxJsonBytes, name).also { totalBytes += it.size }
                        name.startsWith(ATTACH_PREFIX) -> {
                            val rel = name.removePrefix(ATTACH_PREFIX)
                            requireSafeRelative(rel)
                            val target = File(stagingDir, "a" + staged.size)
                            val md = MessageDigest.getInstance("SHA-256")
                            var n = 0L
                            target.outputStream().use { os ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = zip.read(buf)
                                    if (r < 0) break
                                    n += r
                                    totalBytes += r
                                    if (totalBytes > limits.maxTotalBytes) throw ArchiveException("Archive exceeds size limit (${limits.maxTotalBytes} bytes uncompressed).")
                                    md.update(buf, 0, r)
                                    os.write(buf, 0, r)
                                }
                            }
                            staged[rel] = target to ArchiveFile(name, md.digest().toHex(), n)
                        }
                        else -> throw ArchiveException("Unexpected archive entry: $name")
                    }
                    if (totalBytes > limits.maxTotalBytes) throw ArchiveException("Archive exceeds size limit.")
                }
            }
            val mBytes = manifestBytes ?: throw ArchiveException("Not a BLACKBOX archive: manifest.json missing.")
            val manifest = try {
                json.decodeFromString(ArchiveManifest.serializer(), mBytes.decodeToString())
            } catch (e: Exception) {
                throw ArchiveException("Malformed manifest: ${e.message?.take(200)}")
            }
            if (manifest.format != ArchiveManifest.FORMAT) throw ArchiveException("Unknown archive format '${manifest.format}'.")
            if (manifest.formatVersion > ArchiveManifest.VERSION) {
                throw ArchiveException("Archive format v${manifest.formatVersion} is newer than this app supports (v${ArchiveManifest.VERSION}).")
            }
            val bBytes = bundleBytes ?: throw ArchiveException("Archive is missing bundle.json.")
            if (sha256(bBytes) != manifest.bundleSha256) throw ArchiveException("bundle.json checksum mismatch; archive is corrupt or modified.")
            val bundle = try {
                json.decodeFromString(ProjectBundle.serializer(), bBytes.decodeToString())
            } catch (e: Exception) {
                throw ArchiveException("Malformed bundle.json: ${e.message?.take(200)}")
            }
            // Every manifest file must be present with a matching checksum, and nothing extra.
            val byPath = staged.values.associateBy({ it.second.path }, { it.second })
            for (f in manifest.files) {
                val actual = byPath[f.path] ?: throw ArchiveException("Archive is missing ${f.path}.")
                if (actual.sha256 != f.sha256 || actual.bytes != f.bytes) throw ArchiveException("Checksum mismatch for ${f.path}.")
            }
            val declared = manifest.files.map { it.path }.toSet()
            byPath.keys.firstOrNull { it !in declared }?.let { throw ArchiveException("Undeclared archive entry: $it") }
            if (bundle.project.id != manifest.projectId) throw ArchiveException("Manifest/project id mismatch.")
            val problems = BundleValidator.validate(bundle)
            if (problems.isNotEmpty()) throw ArchiveException("Archive records are inconsistent: " + problems.take(5).joinToString("; "))
            return ReadArchive(manifest, bundle, staged.mapValues { it.value.first })
        } catch (e: Exception) {
            staged.values.forEach { it.first.delete() }
            if (e is ArchiveException) throw e
            throw ArchiveException("Could not read archive: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
        }
    }

    private fun readBounded(ins: InputStream, max: Long, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw ArchiveException("$name exceeds $max bytes.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Rejects traversal, absolute paths, drive letters, backslashes and control chars. */
    fun validateEntryName(name: String) {
        if (name.isEmpty()) throw ArchiveException("Empty archive entry name.")
        if (name.startsWith("/") || name.contains('\\') || name.contains(':') || name.any { it.code < 0x20 }) {
            throw ArchiveException("Unsafe archive entry name: $name")
        }
        if (name.split('/').any { it == ".." || it == "." || it.isEmpty() }) {
            throw ArchiveException("Unsafe archive entry name: $name")
        }
    }

    fun requireSafeRelative(path: String) = validateEntryName(path)

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
