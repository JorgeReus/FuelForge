package com.reus.nutri

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.reus.nutri.db.NutriDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking

class CheckInRepositoryTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = NutriDatabase(driver).also { NutriDatabase.Schema.create(driver) }

    @AfterTest
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun savingCheckInQueuesAnUpsert() {
        val checkIn = DailyCheckIn(id = "check-in-1", recordedOn = "2026-09-20")

        saveLocalCheckIn(database, checkIn, nowEpochMs = 1L)

        val mutation = pendingLocalMutations(database).single()
        assertEquals("check-in-1", mutation.entityId)
        assertEquals("upsert", mutation.operation)
    }

    @Test
    fun savingCheckInRoundTripsThroughLocalStorageAndJson() {
        val checkIn = DailyCheckIn(
            id = "check-in-1",
            recordedOn = "2026-09-20",
            weightGrams = 81250,
            sleepMinutes = 465,
            soreness = 6,
            comments = "Legs feel ready.",
        )

        saveLocalCheckIn(database, checkIn, nowEpochMs = 1L)

        assertEquals(checkIn, loadLocalCheckIn(database, checkIn.recordedOn))
        assertEquals(checkIn, Json.decodeFromString<DailyCheckIn>(pendingLocalMutations(database).single().payloadJson))
    }

    @Test
    fun repeatedSameDaySaveKeepsOnlyLatestRowAndMutation() {
        val first = DailyCheckIn(id = "check-in-1", recordedOn = "2026-09-20", soreness = 3)
        val latest = DailyCheckIn(id = "check-in-2", recordedOn = "2026-09-20", soreness = 9)

        saveLocalCheckIn(database, first, nowEpochMs = 1L)
        saveLocalCheckIn(database, latest, nowEpochMs = 2L)

        val saved = assertNotNull(loadLocalCheckIn(database, latest.recordedOn))
        val mutations = pendingLocalMutations(database)
        assertEquals(9, saved.soreness)
        assertEquals(1, mutations.size)
        assertEquals("check-in-1", mutations.single().entityId)
        assertEquals(saved, Json.decodeFromString<DailyCheckIn>(mutations.single().payloadJson))
    }

    @Test
    fun successfulSyncRemovesOnlyTheSentMutation() = runBlocking {
        val checkIn = DailyCheckIn(id = "check-in-1", recordedOn = "2026-09-20")
        val repository = CheckInRepository(
            remoteFetch = { null },
            remoteUpsert = { SyncResult.Synced },
            nowEpochMs = { 1L },
            database = database,
        )

        assertEquals(SyncResult.Synced, repository.save(checkIn))
        assertEquals(emptyList(), pendingLocalMutations(database))
        assertEquals(checkIn, loadLocalCheckIn(database, checkIn.recordedOn))
    }
}
