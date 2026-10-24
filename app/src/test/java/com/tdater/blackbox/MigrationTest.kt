package com.tdater.blackbox

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tdater.blackbox.data.AttachmentStore
import com.tdater.blackbox.data.BlackboxRepository
import com.tdater.blackbox.data.db.BlackboxDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.io.File

/**
 * v1 → v2 must keep existing records. A v1 file is made by building v2 and stripping what v2 added
 * (DROP COLUMN needs the native SQLite mode), then reopened with the real migrations; Room validates the
 * migrated schema against the entities on open, so a mismatch fails here instead of on someone's phone.
 */
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v1DatabaseUpgradesWithoutLosingRecords() = runTest {
        val name = "migrate.db"
        ctx.deleteDatabase(name)
        val fresh = Room.databaseBuilder(ctx, BlackboxDatabase::class.java, name).allowMainThreadQueries().build()
        val repo = BlackboxRepository(fresh, AttachmentStore(File(ctx.filesDir, "attachments-migrate")))
        val pid = repo.createProject("Old project", "made on v1")
        val iid = repo.createInvestigation(pid, "Old problem", "it broke", "it works")
        fresh.close()

        val raw = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("DROP TABLE saved_procedure")
        raw.execSQL("ALTER TABLE project DROP COLUMN templateId")
        raw.execSQL("ALTER TABLE project DROP COLUMN fieldKeys")
        raw.execSQL("ALTER TABLE investigation DROP COLUMN followUpAt")
        raw.execSQL("ALTER TABLE investigation DROP COLUMN followUpNote")
        raw.version = 1
        raw.close()

        val db = Room.databaseBuilder(ctx, BlackboxDatabase::class.java, name)
            .addMigrations(*BlackboxDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val migrated = BlackboxRepository(db, AttachmentStore(File(ctx.filesDir, "attachments-migrate")))
        val b = migrated.loadBundle(pid)!!
        assertEquals("Old project", b.project.title)
        assertEquals("custom", b.project.templateId)
        assertTrue(b.project.fieldKeys.isEmpty())
        val inv = b.investigations.single { it.id == iid }
        assertNull(inv.followUpAt)
        assertEquals("", inv.followUpNote)
        assertTrue(migrated.allProcedures().isEmpty())
        db.close()
    }
}
