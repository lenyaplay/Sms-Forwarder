package com.smsforwarder.gateway.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTextSegmentationTest {

    @Test
    fun `plain text with no special segments is a single Plain segment`() {
        val segments = segmentMessageText("hello there")
        assertEquals(listOf(TextSegment.Plain("hello there")), segments)
    }

    @Test
    fun `link is detected`() {
        val segments = segmentMessageText("visit https://example.com/x today")
        val link = segments.filterIsInstance<TextSegment.Link>().single()
        assertEquals("https://example.com/x", link.text)
    }

    @Test
    fun `phone number is detected`() {
        val segments = segmentMessageText("call +1 555 010 1234 now")
        val phone = segments.filterIsInstance<TextSegment.Phone>().single()
        assertTrue(phone.text.contains("555"))
    }

    @Test
    fun `standalone 4-6 digit number is otp`() {
        val segments = segmentMessageText("code 123456 expires soon")
        val otp = segments.filterIsInstance<TextSegment.Otp>().single()
        assertEquals("123456", otp.text)
    }

    @Test
    fun `dash-split six digit otp is detected as a single otp segment with dash stripped from code`() {
        val segments = segmentMessageText("204-503 - код для входа")
        val otp = segments.filterIsInstance<TextSegment.Otp>().single()
        assertEquals("204-503", otp.text)
        assertEquals("204503", otp.code)
    }

    @Test
    fun `digit run not on a word boundary is not otp`() {
        val segments = segmentMessageText("id12345x")
        assertTrue(segments.none { it is TextSegment.Otp })
    }

    @Test
    fun `mixed text produces ordered segments`() {
        val segments = segmentMessageText("code 1234 see https://a.com")
        assertEquals(4, segments.size)
        assertTrue(segments[0] is TextSegment.Plain)
        assertTrue(segments[1] is TextSegment.Otp)
        assertTrue(segments[2] is TextSegment.Plain)
        assertTrue(segments[3] is TextSegment.Link)
    }
}
