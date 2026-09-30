package ge.hackerman.gza.core.data.model

/** Why a refresh failed, as a screen can explain it. Gateway exceptions never leave `:core:data`. */
enum class SyncError {
    /** No connection, DNS, timeout. */
    OFFLINE,

    /** No usable gateway key, or the gateway rejected the one we have (401/403). */
    NO_KEY,

    /** The gateway answered with an error status. */
    SERVER,

    /** The gateway answered, but not with anything usable. */
    MALFORMED,

    /** The gateway sent nothing, or far less than we have: the cache is kept. */
    EMPTY_OR_SHRUNK,

    /** The local database or a file failed (disk full, I/O). */
    STORAGE
}
