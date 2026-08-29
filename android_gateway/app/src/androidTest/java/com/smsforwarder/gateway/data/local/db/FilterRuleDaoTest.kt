package com.smsforwarder.gateway.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterRuleDaoTest {

    private lateinit var database: GatewayDatabase
    private lateinit var dao: FilterRuleDao

    private fun rule(
        stage: FilterStage = FilterStage.RECEPTION,
        senderPattern: String? = "Bank",
        sortOrder: Int = 0,
    ) = FilterRuleEntity(
        stage = stage,
        senderPattern = senderPattern,
        senderIsRegex = false,
        subscriptionId = null,
        contentPattern = null,
        contentIsRegex = false,
        enabled = true,
        sortOrder = sortOrder,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.filterRuleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertThenGetByIdReturnsTheStoredRule() = runBlocking {
        val id = dao.upsert(rule())

        val stored = dao.getById(id)

        assertEquals("Bank", stored?.senderPattern)
    }

    @Test
    fun upsertWithExistingIdReplacesTheRow() = runBlocking {
        val id = dao.upsert(rule(senderPattern = "Bank"))
        dao.upsert(rule(senderPattern = "Other").copy(id = id))

        val stored = dao.getById(id)

        assertEquals("Other", stored?.senderPattern)
        assertEquals(1, dao.observeRules(FilterStage.RECEPTION).first().size)
    }

    @Test
    fun deleteByIdRemovesOnlyThatRule() = runBlocking {
        val keep = dao.upsert(rule(senderPattern = "Keep"))
        val remove = dao.upsert(rule(senderPattern = "Remove"))

        dao.deleteById(remove)

        val remaining = dao.observeRules(FilterStage.RECEPTION).first()
        assertEquals(1, remaining.size)
        assertEquals(keep, remaining[0].id)
    }

    @Test
    fun observeRulesDoesNotMixReceptionAndForwarding() = runBlocking {
        dao.upsert(rule(stage = FilterStage.RECEPTION))
        dao.upsert(rule(stage = FilterStage.FORWARDING))

        assertEquals(1, dao.observeRules(FilterStage.RECEPTION).first().size)
        assertEquals(1, dao.observeRules(FilterStage.FORWARDING).first().size)
    }

    @Test
    fun observeRulesOrdersBySortOrderAscending() = runBlocking {
        dao.upsert(rule(senderPattern = "Second", sortOrder = 1))
        dao.upsert(rule(senderPattern = "First", sortOrder = 0))

        val ordered = dao.observeRules(FilterStage.RECEPTION).first()

        assertEquals(listOf("First", "Second"), ordered.map { it.senderPattern })
    }

    @Test
    fun getByIdReturnsNullForUnknownId() = runBlocking {
        assertNull(dao.getById(999L))
    }

    @Test
    fun nullableFieldsRoundTripCorrectly() = runBlocking {
        val id = dao.upsert(
            rule().copy(
                senderPattern = null,
                subscriptionId = 5,
                contentPattern = "OTP",
                contentIsRegex = true,
            )
        )

        val stored = dao.getById(id)

        assertNull(stored?.senderPattern)
        assertEquals(5, stored?.subscriptionId)
        assertTrue(stored?.contentIsRegex == true)
    }
}
