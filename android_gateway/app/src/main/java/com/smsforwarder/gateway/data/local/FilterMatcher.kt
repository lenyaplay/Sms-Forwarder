package com.smsforwarder.gateway.data.local

import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity

/**
 * A rule with a subscriptionId that isn't currently active never matches - a
 * rule created for a SIM that was swapped out must not silently start (or
 * stop) applying to whatever card is in the phone now (spec 0015, "Обработка
 * недоступной SIM").
 */
fun matchesRule(
    sender: String,
    subscriptionId: Int?,
    text: String,
    rule: FilterRuleEntity,
    activeSubscriptionIds: Set<Int>,
): Boolean {
    if (!rule.enabled) return false

    if (rule.subscriptionId != null) {
        if (rule.subscriptionId !in activeSubscriptionIds) return false
        if (subscriptionId != rule.subscriptionId) return false
    }

    if (!rule.senderPattern.isNullOrEmpty() && !matchesSenderPattern(sender, rule.senderPattern, rule.senderIsRegex)) {
        return false
    }

    if (!rule.contentPattern.isNullOrEmpty() && !matchesContentPattern(text, rule.contentPattern, rule.contentIsRegex)) {
        return false
    }

    return true
}

/** Exact match when not regex - a sender/short-code is expected to match in full. */
private fun matchesSenderPattern(value: String, pattern: String, isRegex: Boolean): Boolean =
    if (isRegex) matchesRegex(pattern, value) else value == pattern

/**
 * Substring match when not regex - unlike sender, the whole SMS body is
 * essentially never exactly equal to a short keyword, so "not regex" here
 * means "contains", not "equals" (spec 0015, found via live device testing
 * 2026-08-29, confirmed with the product owner before changing).
 */
private fun matchesContentPattern(value: String, pattern: String, isRegex: Boolean): Boolean =
    if (isRegex) matchesRegex(pattern, value) else value.contains(pattern)

private fun matchesRegex(pattern: String, value: String): Boolean =
    runCatching { Regex(pattern).containsMatchIn(value) }.getOrDefault(false)

/**
 * First-match-wins over [rules] (expected pre-filtered to one stage, ordered
 * by sortOrder). Returns true when the message should proceed past this stage.
 */
fun resolveFilterDecision(
    sender: String,
    subscriptionId: Int?,
    text: String,
    rules: List<FilterRuleEntity>,
    mode: FilterMode,
    activeSubscriptionIds: Set<Int>,
): Boolean {
    val matched = rules.firstOrNull { matchesRule(sender, subscriptionId, text, it, activeSubscriptionIds) }
    val default = mode == FilterMode.BLACKLIST
    if (matched == null) return default
    return mode == FilterMode.WHITELIST
}
