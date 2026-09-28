package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CountdownTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun assertReads(remaining: Duration, sentence: String) {
        composeRule.setContent { GzaTheme { LeaveCountdown(remaining) } }
        composeRule.onNodeWithContentDescription(sentence).assertExists()
    }

    @Test
    fun minutes() = assertReads(Duration.ofMinutes(7), "Leave in 7 minutes")

    @Test
    fun oneMinuteIsSingular() = assertReads(Duration.ofSeconds(90), "Leave in 1 minute")

    @Test
    fun seconds() = assertReads(Duration.ofSeconds(42), "Leave in 42 seconds")

    @Test
    fun now() = assertReads(Duration.ZERO, "Leave now")

    @Test
    fun hours() = assertReads(Duration.ofMinutes(65), "Leave in 1 h 5 min")

    @Test
    @Config(qualifiers = "+ka")
    fun georgian() = assertReads(Duration.ofMinutes(7), "გამოდით 7 წუთში")
}
