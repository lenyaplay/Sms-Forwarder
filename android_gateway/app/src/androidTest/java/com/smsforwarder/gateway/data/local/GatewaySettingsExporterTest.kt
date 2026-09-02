package com.smsforwarder.gateway.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.local.db.GatewayDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GatewaySettingsExporterTest {

    private lateinit var database: GatewayDatabase
    private lateinit var configStore: GatewayConfigStore
    private lateinit var exporter: GatewaySettingsExporter
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java).allowMainThreadQueries().build()
        configStore = GatewayConfigStore(context)
        exporter = GatewaySettingsExporter(configStore, database.filterRuleDao(), Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun seedSettings() = runBlocking {
        configStore.save("https://example.com", "tok-123")
        configStore.setRetryMaxAttempts(5)
        configStore.setRetryBaseIntervalSeconds(90L)
        configStore.setRetryBackoffPolicy(BackoffPolicy.LINEAR)
        database.filterRuleDao().upsert(
            FilterRuleEntity(
                stage = FilterStage.RECEPTION,
                senderPattern = "\\+1555.*",
                senderIsRegex = true,
                subscriptionId = null,
                contentPattern = "spam",
                contentIsRegex = false,
                enabled = true,
                sortOrder = 0,
            )
        )
    }

    @Test
    fun exportThenImportOnAFreshStoreRestoresIdenticalValues() = runBlocking {
        seedSettings()
        val json = exporter.exportToJson()

        // Wipe everything, like a fresh install would be.
        context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
        database.filterRuleDao().deleteAll()
        val freshConfigStore = GatewayConfigStore(context)
        val freshExporter = GatewaySettingsExporter(freshConfigStore, database.filterRuleDao(), Json { ignoreUnknownKeys = true })

        val result = freshExporter.importFromJson(json)

        assertTrue(result.isSuccess)
        assertEquals("https://example.com", freshConfigStore.getServerUrl())
        assertEquals("tok-123", freshConfigStore.getUploadToken())
        assertEquals(5, freshConfigStore.retryMaxAttempts())
        assertEquals(90L, freshConfigStore.retryBaseIntervalSeconds())
        assertEquals(BackoffPolicy.LINEAR, freshConfigStore.retryBackoffPolicy())
        val rules = database.filterRuleDao().getAll()
        assertEquals(1, rules.size)
        assertEquals("\\+1555.*", rules[0].senderPattern)
        assertEquals(true, rules[0].senderIsRegex)
        assertEquals("spam", rules[0].contentPattern)
    }

    @Test
    fun importReplacesExistingFilterRulesRatherThanMerging() = runBlocking {
        seedSettings()
        val json = exporter.exportToJson()

        // A rule that exists on the device only AFTER the export was taken -
        // simulating "the file doesn't know about this rule" - must not survive the import.
        database.filterRuleDao().upsert(
            FilterRuleEntity(
                stage = FilterStage.FORWARDING,
                senderPattern = "old-rule",
                senderIsRegex = false,
                subscriptionId = null,
                contentPattern = null,
                contentIsRegex = false,
                enabled = true,
                sortOrder = 0,
            )
        )

        exporter.importFromJson(json)

        val rules = database.filterRuleDao().getAll()
        assertEquals(1, rules.size)
        assertEquals("\\+1555.*", rules[0].senderPattern)
    }

    @Test
    fun importWithOutOfRangeRetryMaxAttemptsAppliesNothing() = runBlocking {
        seedSettings()
        val badJson = """{"serverUrl":"https://evil.example.com","uploadToken":"evil","retryMaxAttempts":999,"retryBaseIntervalSeconds":30,"retryBackoffPolicy":"EXPONENTIAL","filterRules":[]}"""

        val result = exporter.importFromJson(badJson)

        assertTrue(result.isFailure)
        assertEquals("https://example.com", configStore.getServerUrl())
        assertEquals(5, configStore.retryMaxAttempts())
    }

    @Test
    fun importWithInvalidRegexAppliesNothing() = runBlocking {
        seedSettings()
        val badJson = """{"serverUrl":null,"uploadToken":null,"retryMaxAttempts":10,"retryBaseIntervalSeconds":30,"retryBackoffPolicy":"EXPONENTIAL",""" +
            """"filterRules":[{"stage":"RECEPTION","senderPattern":"[unclosed","senderIsRegex":true,"subscriptionId":null,"contentPattern":null,"contentIsRegex":false,"enabled":true,"sortOrder":0}]}"""

        val result = exporter.importFromJson(badJson)

        assertTrue(result.isFailure)
        val rules = database.filterRuleDao().getAll()
        assertEquals(1, rules.size)
        assertEquals("\\+1555.*", rules[0].senderPattern)
    }

    @Test
    fun importWithMalformedJsonAppliesNothing() = runBlocking {
        seedSettings()

        val result = exporter.importFromJson("not valid json at all")

        assertTrue(result.isFailure)
        assertEquals(5, configStore.retryMaxAttempts())
    }
}
