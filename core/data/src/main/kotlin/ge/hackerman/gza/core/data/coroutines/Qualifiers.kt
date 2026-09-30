package ge.hackerman.gza.core.data.coroutines

import javax.inject.Qualifier

// Here rather than in :app so the data layer can ask for them; :app provides them.

/** Blocking I/O dispatcher. Injected so tests can swap it; never hardcode Dispatchers.IO in classes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU dispatcher, for work like decoding a large response. Injected for the same reason. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Lives as long as the process, for work no screen owns, such as background config refreshes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
