package ge.hackerman.gza.core.model

/** A route colour as `0xRRGGBB`, no alpha. */
@JvmInline
value class RouteColor(val rgb: Int) {
    init {
        require(rgb in 0..MAX_RGB) { "colour must be 0xRRGGBB" }
    }

    companion object {
        /** Parses `00B38B`, `ff505b` or `#00B38B`; anything else is null. */
        fun ofHexOrNull(raw: String?): RouteColor? {
            val hex = raw?.trim()?.removePrefix("#")
            return if (hex != null && hex.length == HEX_LENGTH && hex.all { it.isHexDigit() }) {
                RouteColor(hex.toInt(HEX_RADIX))
            } else {
                null
            }
        }

        private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}

private const val MAX_RGB = 0xFFFFFF
private const val HEX_LENGTH = 6
private const val HEX_RADIX = 16
