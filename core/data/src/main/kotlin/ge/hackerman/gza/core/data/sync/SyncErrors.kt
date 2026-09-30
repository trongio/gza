package ge.hackerman.gza.core.data.sync

import android.database.SQLException
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

/**
 * Maps what a sync can throw to what a screen can say. Anything else is a bug and is rethrown,
 * so tests see it instead of a disguised "server error". Cancellation always propagates.
 * `androidx.sqlite.SQLiteException` is `android.database.SQLException` on Android.
 */
internal fun Throwable.toSyncError(): SyncError = when (this) {
    is CancellationException -> throw this

    is TtcGatewayException.Network -> SyncError.OFFLINE

    is TtcGatewayException.NoKey -> SyncError.NO_KEY

    // After the interceptor's own refetch: the key is dead. The next cadence retries.
    is TtcGatewayException.Http ->
        if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) SyncError.NO_KEY else SyncError.SERVER

    is TtcGatewayException.Malformed -> SyncError.MALFORMED

    is SQLException, is IOException -> SyncError.STORAGE

    else -> throw this
}

/** Worth retrying soon; a storage failure will not fix itself with a retry. */
internal fun SyncError.isRetryable(): Boolean = this != SyncError.STORAGE
