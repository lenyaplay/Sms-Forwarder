package com.smsforwarder.gateway.data.repository

import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.SimOptionsProvider
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleDao
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.local.resolveFilterDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FilterRuleRepository @Inject constructor(
    private val filterRuleDao: FilterRuleDao,
    private val configStore: GatewayConfigStore,
    private val simOptionsProvider: SimOptionsProvider,
) {
    open fun observeRules(stage: FilterStage): Flow<List<FilterRuleEntity>> = filterRuleDao.observeRules(stage)

    open suspend fun getRule(id: Long): FilterRuleEntity? = filterRuleDao.getById(id)

    open suspend fun upsert(rule: FilterRuleEntity): Long = filterRuleDao.upsert(rule)

    open suspend fun delete(id: Long) = filterRuleDao.deleteById(id)

    open fun getMode(stage: FilterStage): FilterMode = configStore.filterMode(stage)

    open fun setMode(stage: FilterStage, mode: FilterMode) = configStore.setFilterMode(stage, mode)

    open fun activeSubscriptionIds(): Set<Int> = simOptionsProvider.activeSims().map { it.subscriptionId }.toSet()

    open fun availableSims(): List<SimOption> = simOptionsProvider.activeSims()

    /** true = the message proceeds past [stage]; false = it's blocked there (see spec 0015 for what each stage's block means). */
    open suspend fun shouldAccept(stage: FilterStage, sender: String, subscriptionId: Int?, text: String): Boolean {
        val rules = filterRuleDao.observeRules(stage).first()
        val mode = configStore.filterMode(stage)
        return resolveFilterDecision(sender, subscriptionId, text, rules, mode, activeSubscriptionIds())
    }
}
