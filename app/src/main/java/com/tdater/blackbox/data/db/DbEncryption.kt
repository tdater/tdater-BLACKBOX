package com.tdater.blackbox.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The records database is encrypted with SQLCipher. Its random 256-bit passphrase is stored wrapped by an Android
 * Keystore key, so it never leaves the phone in usable form and is gone when the app is uninstalled.
 *
 * Databases created before encryption existed are converted once, on first open: the plaintext file is exported into
 * an encrypted copy, the copy is checked, and only then swapped in. If anything fails the plaintext file is left as it
 * was and opened as before, so a failed conversion never loses records.
 */
object DbEncryption {
    private const val TAG = "DbEncryption"
    private const val KEY_ALIAS = "blackbox-db-wrap"
    private const val PREFS = "blackbox_db_key"
    private const val PREF_WRAPPED = "wrapped"

    fun factory(context: Context, name: String): SupportOpenHelperFactory? = try {
        System.loadLibrary("sqlcipher")
        val pass = passphrase(context)
        val file = context.getDatabasePath(name)
        if (file.exists() && isPlaintext(file)) encryptInPlace(file, pass)
        if (file.exists() && isPlaintext(file)) null else SupportOpenHelperFactory(pass)
    } catch (t: Throwable) {
        // Keystore or native library trouble: keep working unencrypted rather than lock the user out of their records.
        Log.e(TAG, "Encryption unavailable; opening without it", t)
        if (context.getDatabasePath(name).let { it.exists() && !isPlaintext(it) }) throw t
        null
    }

    fun isEncrypted(context: Context, name: String): Boolean = context.getDatabasePath(name).let { it.exists() && !isPlaintext(it) }

    private fun isPlaintext(file: File): Boolean = file.inputStream().use { input ->
        val header = ByteArray(16)
        input.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
    }

    private fun encryptInPlace(file: File, pass: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".enc-tmp")
        tmp.delete()
        val hex = pass.joinToString("") { "%02x".format(it) }
        val plain = SQLiteDatabase.openDatabase(file.path, ByteArray(0), null, SQLiteDatabase.OPEN_READWRITE, null)
        try {
            val version = plain.version
            plain.rawExecSQL("ATTACH DATABASE '${tmp.path.replace("'", "''")}' AS encrypted KEY \"x'$hex'\"")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }
        // Check the copy opens with the key and has the same tables before replacing anything.
        val check = SQLiteDatabase.openDatabase(tmp.path, pass, null, SQLiteDatabase.OPEN_READONLY, null)
        val ok = try {
            check.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='project'", null).use { it.moveToFirst() && it.getInt(0) == 1 }
        } finally {
            check.close()
        }
        if (!ok) { tmp.delete(); throw IllegalStateException("Encrypted copy failed verification") }
        listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        if (!tmp.renameTo(file)) { tmp.delete(); throw IllegalStateException("Could not replace the database file") }
    }

    private fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(PREF_WRAPPED, null)?.let { return unwrap(Base64.decode(it, Base64.NO_WRAP)) }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(PREF_WRAPPED, Base64.encodeToString(wrap(pass), Base64.NO_WRAP)).commit()
        return pass
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return gen.generateKey()
    }

    private fun wrap(pass: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        return c.iv + c.doFinal(pass)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv)) }
        return c.doFinal(blob, 12, blob.size - 12)
    }
}
