package ge.hackerman.gza.core.model

/**
 * A stop id as the gateway sends it, always feed-prefixed: `1:970`, `1:Metro_Metro_1`.
 */
@JvmInline
value class StopId(val value: String) {
    init {
        requireFeedPrefixed(value)
    }

    val feedId: String get() = value.substringBefore(FEED_SEPARATOR)
    val localId: String get() = value.substringAfter(FEED_SEPARATOR)
}

/**
 * A route id as the gateway sends it, always feed-prefixed: `1:R97493`, `1:minibusR24579`.
 */
@JvmInline
value class RouteId(val value: String) {
    init {
        requireFeedPrefixed(value)
    }

    val feedId: String get() = value.substringBefore(FEED_SEPARATOR)
    val localId: String get() = value.substringAfter(FEED_SEPARATOR)
}

private const val FEED_SEPARATOR = ':'

// The gateway rejects unprefixed ids, so an id without a feed is a bug at the call site.
private fun requireFeedPrefixed(value: String) {
    require(value.isNotBlank()) { "id must not be blank" }
    val separator = value.indexOf(FEED_SEPARATOR)
    require(separator > 0 && separator < value.length - 1) { "id must be feed-prefixed like 1:970: $value" }
}
