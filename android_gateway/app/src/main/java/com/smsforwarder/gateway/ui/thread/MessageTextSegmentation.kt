package com.smsforwarder.gateway.ui.thread

sealed class TextSegment {
    abstract val text: String

    data class Plain(override val text: String) : TextSegment()
    data class Link(override val text: String, val url: String) : TextSegment()
    data class Phone(override val text: String, val number: String) : TextSegment()
    data class Otp(override val text: String, val code: String) : TextSegment()
}

private val linkRegex = Regex("""https?://\S+""")

// International, not tied to a specific country - the project handles SMS from
// any sender. At least 7 significant digits, optionally grouped with spaces/dashes,
// optional leading '+'.
private val phoneRegex = Regex("""\+?(?:\d[\s-]?){7,}\d""")

// Standalone (word-boundary) 4-6 digit run, optionally split by a single dash (some
// senders format OTPs as e.g. "204-503"). Spec 0031 decision: any such number is
// treated as OTP, no context-word heuristic - false positives on times/years are an
// accepted tradeoff, not a defect. The dash-split alternative is filtered by total
// digit count below, since the regex alone can't count digits across both groups.
private val otpRegex = Regex("""\b\d{4,6}\b|\b\d{1,3}-\d{1,3}\b""")

/** Segments [text] into plain/link/phone/otp runs. Checked in that priority order so ranges never overlap. */
fun segmentMessageText(text: String): List<TextSegment> {
    val found = mutableListOf<Pair<IntRange, (String) -> TextSegment>>()

    linkRegex.findAll(text).forEach { match ->
        found += match.range to { s: String -> TextSegment.Link(s, s) }
    }
    phoneRegex.findAll(text).forEach { match ->
        if (found.none { it.first.overlaps(match.range) }) {
            found += match.range to { s: String -> TextSegment.Phone(s, s) }
        }
    }
    otpRegex.findAll(text).forEach { match ->
        val digitCount = match.value.count { it.isDigit() }
        if (digitCount in 4..6 && found.none { it.first.overlaps(match.range) }) {
            found += match.range to { s: String -> TextSegment.Otp(s, s.filter { it.isDigit() }) }
        }
    }

    val sorted = found.sortedBy { it.first.first }
    val segments = mutableListOf<TextSegment>()
    var cursor = 0
    for ((range, factory) in sorted) {
        if (range.first > cursor) {
            segments += TextSegment.Plain(text.substring(cursor, range.first))
        }
        segments += factory(text.substring(range.first, range.last + 1))
        cursor = range.last + 1
    }
    if (cursor < text.length) {
        segments += TextSegment.Plain(text.substring(cursor))
    }
    return segments
}

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last
