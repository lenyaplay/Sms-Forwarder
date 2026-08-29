package com.smsforwarder.gateway.data.local

import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMatcherTest {

    private fun rule(
        senderPattern: String? = null,
        senderIsRegex: Boolean = false,
        subscriptionId: Int? = null,
        contentPattern: String? = null,
        contentIsRegex: Boolean = false,
        enabled: Boolean = true,
    ) = FilterRuleEntity(
        stage = FilterStage.RECEPTION,
        senderPattern = senderPattern,
        senderIsRegex = senderIsRegex,
        subscriptionId = subscriptionId,
        contentPattern = contentPattern,
        contentIsRegex = contentIsRegex,
        enabled = enabled,
        sortOrder = 0,
    )

    // matchesRule

    @Test
    fun matchesOnSenderExactOnly() {
        val r = rule(senderPattern = "Bank")
        assertTrue(matchesRule("Bank", null, "anything", r, emptySet()))
        assertFalse(matchesRule("OtherBank", null, "anything", r, emptySet()))
    }

    @Test
    fun requiresAllNonEmptyCriteriaToMatchSenderAndContent() {
        val r = rule(senderPattern = "Bank", contentPattern = "OTP")
        assertTrue(matchesRule("Bank", null, "your OTP is 1234", r, emptySet()))
        assertFalse(matchesRule("Bank", null, "welcome", r, emptySet()))
        assertFalse(matchesRule("OtherBank", null, "your OTP is 1234", r, emptySet()))
    }

    @Test
    fun senderRegexMatchesSubstringPattern() {
        val r = rule(senderPattern = "^[A-Z]{2}-\\d+$", senderIsRegex = true)
        assertTrue(matchesRule("AB-1234", null, "x", r, emptySet()))
        assertFalse(matchesRule("ab-1234", null, "x", r, emptySet()))
    }

    @Test
    fun senderNonRegexRequiresExactMatchUnlikeContent() {
        val r = rule(senderPattern = "Bank")
        assertFalse(matchesRule("MyBank", null, "x", r, emptySet()))
    }

    @Test
    fun contentNonRegexMatchesSubstringNotWholeMessage() {
        val r = rule(contentPattern = "STOP")
        assertTrue(matchesRule("s", null, "STOP", r, emptySet()))
        assertTrue(matchesRule("s", null, "please STOP now", r, emptySet()))
        assertFalse(matchesRule("s", null, "please stop now", r, emptySet()))
    }

    @Test
    fun contentRegexVsSubstringAreIndependent() {
        val regex = rule(contentPattern = "^STOP$", contentIsRegex = true)
        assertTrue(matchesRule("s", null, "STOP", regex, emptySet()))
        assertFalse(matchesRule("s", null, "please STOP now", regex, emptySet()))
    }

    @Test
    fun ruleWithSubscriptionIdOutsideActiveSetNeverMatches() {
        val r = rule(subscriptionId = 5)
        assertFalse(matchesRule("s", 5, "x", r, activeSubscriptionIds = emptySet()))
        assertFalse(matchesRule("s", 5, "x", r, activeSubscriptionIds = setOf(1, 2)))
        assertTrue(matchesRule("s", 5, "x", r, activeSubscriptionIds = setOf(5)))
    }

    @Test
    fun ruleWithSubscriptionIdDoesNotMatchDifferentActiveSubscription() {
        val r = rule(subscriptionId = 5)
        assertFalse(matchesRule("s", 7, "x", r, activeSubscriptionIds = setOf(5, 7)))
    }

    @Test
    fun disabledRuleNeverMatches() {
        val r = rule(senderPattern = "Bank", enabled = false)
        assertFalse(matchesRule("Bank", null, "x", r, emptySet()))
    }

    @Test
    fun ruleWithNoCriteriaMatchesAnything() {
        val r = rule()
        assertTrue(matchesRule("anyone", null, "any text", r, emptySet()))
    }

    // resolveFilterDecision

    @Test
    fun blacklistDefaultsToAllowWhenNothingMatches() {
        val decision = resolveFilterDecision("s", null, "x", emptyList(), FilterMode.BLACKLIST, emptySet())
        assertTrue(decision)
    }

    @Test
    fun whitelistDefaultsToBlockWhenNothingMatches() {
        val decision = resolveFilterDecision("s", null, "x", emptyList(), FilterMode.WHITELIST, emptySet())
        assertFalse(decision)
    }

    @Test
    fun blacklistMatchedRuleBlocks() {
        val rules = listOf(rule(senderPattern = "Spam"))
        assertFalse(resolveFilterDecision("Spam", null, "x", rules, FilterMode.BLACKLIST, emptySet()))
    }

    @Test
    fun whitelistMatchedRuleAllows() {
        val rules = listOf(rule(senderPattern = "Bank"))
        assertTrue(resolveFilterDecision("Bank", null, "x", rules, FilterMode.WHITELIST, emptySet()))
    }

    @Test
    fun firstMatchWinsOverLaterRules() {
        val rules = listOf(
            rule(senderPattern = "Bank", enabled = true),
            rule(senderPattern = "Bank", enabled = true, contentPattern = "never checked"),
        )
        // Both rules would match sender "Bank"; first one in the list decides, order matters.
        assertFalse(resolveFilterDecision("Bank", null, "x", rules, FilterMode.BLACKLIST, emptySet()))
    }

    @Test
    fun disabledRuleIsSkippedFallingThroughToDefault() {
        val rules = listOf(rule(senderPattern = "Bank", enabled = false))
        assertTrue(resolveFilterDecision("Bank", null, "x", rules, FilterMode.BLACKLIST, emptySet()))
    }
}
