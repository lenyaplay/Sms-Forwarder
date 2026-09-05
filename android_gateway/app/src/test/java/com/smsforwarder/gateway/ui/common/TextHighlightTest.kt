package com.smsforwarder.gateway.ui.common

import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TextHighlightTest {

    private val style = SpanStyle(background = androidx.compose.ui.graphics.Color.Red)

    @Test
    fun `blank query returns unstyled text`() {
        val result = highlightedText("hello world", "", style)
        assertEquals("hello world", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `single match is highlighted`() {
        val result = highlightedText("hello world", "world", style)
        assertEquals(1, result.spanStyles.size)
        assertEquals(6, result.spanStyles[0].start)
        assertEquals(11, result.spanStyles[0].end)
    }

    @Test
    fun `match is case insensitive`() {
        val result = highlightedText("Hello World", "world", style)
        assertEquals(1, result.spanStyles.size)
        assertEquals(6, result.spanStyles[0].start)
        assertEquals(11, result.spanStyles[0].end)
    }

    @Test
    fun `multiple occurrences are all highlighted`() {
        val result = highlightedText("ab ab ab", "ab", style)
        assertEquals(3, result.spanStyles.size)
    }

    @Test
    fun `no match leaves text unstyled`() {
        val result = highlightedText("hello world", "xyz", style)
        assertEquals("hello world", result.text)
        assertEquals(0, result.spanStyles.size)
    }
}
