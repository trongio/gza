package ge.hackerman.gza.core.data.datastore

/**
 * DataStore file names, under `files/datastore/` (`Context.dataStoreFile`). The app's backup
 * rules (`app/src/main/res/xml/`) name these paths: change both together.
 */
object DataStoreFiles {
    /** Saved stops, walk times, route filters, buffer. Backed up. */
    const val USER_PREFERENCES: String = "user_prefs.json"

    /** Gateway key and Firebase installation. Never backed up: device specific and rotates. */
    const val TTC_CONFIG: String = "ttc_config.json"
}
