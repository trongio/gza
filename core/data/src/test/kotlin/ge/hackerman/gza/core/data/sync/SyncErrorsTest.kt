package ge.hackerman.gza.core.data.sync

import android.database.SQLException
import android.database.sqlite.SQLiteFullException
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric: android.database exceptions cannot be built on the plain JVM stubs.
@RunWith(AndroidJUnit4::class)
class SyncErrorsTest {
    @Test
    fun `gateway failures map to what a screen can say`() {
        assertEquals(SyncError.OFFLINE, TtcGatewayException.Network(IOException()).toSyncError())
        assertEquals(SyncError.NO_KEY, TtcGatewayException.NoKey(IOException()).toSyncError())
        assertEquals(SyncError.NO_KEY, TtcGatewayException.Http(401, null).toSyncError())
        assertEquals(SyncError.NO_KEY, TtcGatewayException.Http(403, null).toSyncError())
        assertEquals(SyncError.SERVER, TtcGatewayException.Http(500, null).toSyncError())
        assertEquals(SyncError.SERVER, TtcGatewayException.Http(404, null).toSyncError())
        assertEquals(SyncError.MALFORMED, TtcGatewayException.Malformed(null).toSyncError())
    }

    @Test
    fun `database and file failures are storage`() {
        assertEquals(SyncError.STORAGE, SQLException("disk I/O error").toSyncError())
        assertEquals(SyncError.STORAGE, SQLiteFullException("database or disk is full").toSyncError())
        assertEquals(SyncError.STORAGE, IOException("no space").toSyncError())
    }

    @Test
    fun `cancellation and bugs are rethrown, never disguised`() {
        val cancel = CancellationException("cancelled")
        assertSame(cancel, assertThrows(CancellationException::class.java) { cancel.toSyncError() })
        val bug = IllegalStateException("bug")
        assertSame(bug, assertThrows(IllegalStateException::class.java) { bug.toSyncError() })
    }

    @Test
    fun `only storage is not worth a retry`() {
        SyncError.entries.forEach { assertEquals(it != SyncError.STORAGE, it.isRetryable()) }
        assertTrue(SyncError.OFFLINE.isRetryable())
        assertFalse(SyncError.STORAGE.isRetryable())
    }
}
