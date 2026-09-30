package ge.hackerman.gza.core.model

/**
 * App-wide preferences. [bufferMinutes] is the slack added to the walk before "leave now"
 * (PLAN.md 2.3); [firstRunSuggestionHandled] records that stop 1:970 was offered once.
 */
data class UserSettings(val bufferMinutes: Int, val firstRunSuggestionHandled: Boolean) {
    init {
        require(bufferMinutes in BUFFER_MINUTES_RANGE) { "buffer minutes out of range: $bufferMinutes" }
    }

    companion object {
        val BUFFER_MINUTES_RANGE: IntRange = 0..3
        const val DEFAULT_BUFFER_MINUTES: Int = 1
    }
}
