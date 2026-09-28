package ge.hackerman.gza.core.testing

/** Parameters for `ParameterizedRobolectricTestRunner`: a name and whether the theme is dark. */
object ThemeParameters {
    @JvmStatic
    fun both(): List<Array<Any>> = listOf(arrayOf("light", false), arrayOf("dark", true))
}
