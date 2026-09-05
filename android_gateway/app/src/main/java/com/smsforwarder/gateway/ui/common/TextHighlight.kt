package com.smsforwarder.gateway.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

val defaultHighlightStyle = SpanStyle(background = Color(0x552196F3))

/** Case-insensitive highlighting of every occurrence of [query] in [text]. A blank [query] returns [text] unchanged (no highlighting). */
fun highlightedText(text: String, query: String, highlightStyle: SpanStyle = defaultHighlightStyle): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    return buildAnnotatedString {
        var searchStart = 0
        while (searchStart <= text.length) {
            val matchIndex = text.indexOf(query, searchStart, ignoreCase = true)
            if (matchIndex < 0) {
                append(text.substring(searchStart))
                break
            }
            append(text.substring(searchStart, matchIndex))
            withStyle(highlightStyle) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            searchStart = matchIndex + query.length
        }
    }
}
