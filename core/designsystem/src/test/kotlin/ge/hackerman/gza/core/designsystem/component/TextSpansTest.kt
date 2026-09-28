package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TextSpansTest {
    private val style = SpanStyle(fontWeight = FontWeight.Bold)

    @Test
    fun stylesExactlyTheTime() {
        val text = withMonoSpan("გადის 17:13-ზე", "17:13", style)
        assertEquals("გადის 17:13-ზე", text.text)
        val span = text.spanStyles.single()
        assertEquals(style, span.item)
        assertEquals("17:13", text.text.substring(span.start, span.end))
    }

    @Test
    fun absentValueLeavesThePlainText() {
        val text = withMonoSpan("Leave now", "17:13", style)
        assertEquals("Leave now", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }

    @Test
    fun onlyTheFirstOccurrenceIsStyled() {
        val text = withMonoSpan("17:13 then 17:13", "17:13", style)
        val span = text.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun emptyValueAddsNoSpan() {
        val text = withMonoSpan("Leave by 17:08", "", style)
        assertEquals("Leave by 17:08", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }
}
