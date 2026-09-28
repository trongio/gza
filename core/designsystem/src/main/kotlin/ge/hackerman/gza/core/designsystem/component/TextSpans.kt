package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Styles the first occurrence of [value] in [full], for a time inside a translated
 * sentence (`გადის 17:13-ზე`). The string resource keeps the time as an argument so each
 * language places it naturally. If [value] is empty or absent the text stays plain.
 */
fun withMonoSpan(full: String, value: String, style: SpanStyle): AnnotatedString {
    val start = if (value.isEmpty()) -1 else full.indexOf(value)
    if (start < 0) return AnnotatedString(full)
    return buildAnnotatedString {
        append(full, 0, start)
        withStyle(style) { append(value) }
        append(full, start + value.length, full.length)
    }
}
