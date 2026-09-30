package ge.hackerman.gza.core.ttc.testing

import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import java.io.IOException

/** A [TtcConfigCache] whose installation calls can be made to fail like a broken file store. */
class FlakyInstallationCache(private val delegate: TtcConfigCache) : TtcConfigCache by delegate {
    @Volatile var failReads = false

    @Volatile var failWrites = false

    /** Runs before every installation read, for cancellation tests. */
    @Volatile var onRead: suspend () -> Unit = {}

    override suspend fun readInstallation(): FirebaseInstallation? {
        onRead()
        if (failReads) throw IOException("disk read failed")
        return delegate.readInstallation()
    }

    override suspend fun writeInstallation(installation: FirebaseInstallation?) {
        if (failWrites) throw IOException("disk write failed")
        delegate.writeInstallation(installation)
    }
}
