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

    companion object {
        /** Null instead of an exception, so mappers can drop a bad item without a try/catch. */
        fun ofOrNull(raw: String?): StopId? = raw?.takeIf(::isFeedPrefixed)?.let(::StopId)
    }
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

    /**
     * Minibuses report `mode: BUS` everywhere; only the id tells them apart
     * (`1:minibusR24579`).
     */
    val isMinibus: Boolean get() = localId.startsWith(MINIBUS_PREFIX, ignoreCase = true)

    companion object {
        fun ofOrNull(raw: String?): RouteId? = raw?.takeIf(::isFeedPrefixed)?.let(::RouteId)
    }
}

/** A vehicle id as the positions endpoint sends it, feed-prefixed like stops: `1:981`. */
@JvmInline
value class VehicleId(val value: String) {
    init {
        requireFeedPrefixed(value)
    }

    companion object {
        fun ofOrNull(raw: String?): VehicleId? = raw?.takeIf(::isFeedPrefixed)?.let(::VehicleId)
    }
}

/**
 * One direction variant of a route: `<directionId>:<nn>`, for example `0:01` or `1:03`.
 * Not always `0:01`/`1:01` (route 472 uses `0:03`/`1:03`), so always read it from the route.
 */
@JvmInline
value class PatternSuffix(val value: String) {
    init {
        require(isPatternSuffix(value)) { "pattern suffix must look like 0:01: $value" }
    }

    val directionId: Int get() = value.substringBefore(FEED_SEPARATOR).toInt()

    companion object {
        fun ofOrNull(raw: String?): PatternSuffix? = raw?.takeIf(::isPatternSuffix)?.let(::PatternSuffix)
    }
}

private const val FEED_SEPARATOR = ':'
private const val MINIBUS_PREFIX = "minibus"
private val PATTERN_SUFFIX = Regex("""^\d+:\d+$""")

private fun isFeedPrefixed(value: String): Boolean {
    if (value.isBlank()) return false
    val separator = value.indexOf(FEED_SEPARATOR)
    return separator > 0 && separator < value.length - 1
}

// A long run of digits would overflow directionId, so it is not a valid suffix either.
private fun isPatternSuffix(value: String): Boolean =
    PATTERN_SUFFIX.matches(value) && value.substringBefore(FEED_SEPARATOR).toIntOrNull() != null

// The gateway rejects unprefixed ids, so an id without a feed is a bug at the call site.
private fun requireFeedPrefixed(value: String) {
    require(value.isNotBlank()) { "id must not be blank" }
    require(isFeedPrefixed(value)) { "id must be feed-prefixed like 1:970: $value" }
}
