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
    fun legacyLocalQueueIsAdoptedOnlyWhenItMatchesTheLocalRow() = runBlocking {
        val checkIn = DailyCheckIn("check-in-legacy", "2026-09-20", soreness = 4)
        database.checkInQueries.upsertCheckIn(
            id = checkIn.id,
            recorded_on = checkIn.recordedOn,
            payload_json = Json.encodeToString(checkIn),
            updated_at_epoch_ms = 1L,
        )
        database.checkInQueries.enqueueMutation(
            id = "check-in:${checkIn.id}", entity_type = "patient_check_in",
            entity_id = checkIn.id, operation = "upsert",
            payload_json = Json.encodeToString(checkIn), created_at_epoch_ms = 1L,
        )

        val repository = CheckInRepository({}, { _, _ -> SyncResult.Synced }, { 2L }, { "user-1" }, database)

        assertEquals(SyncResult.Synced, repository.syncPending())
    }

    @Test
    fun legacyQueueWithoutMatchingLocalRowIsNotUploaded() = runBlocking {
        val checkIn = DailyCheckIn("check-in-legacy", "2026-09-20")
        database.checkInQueries.enqueueMutation(
            id = "check-in:${checkIn.id}", entity_type = "patient_check_in",
            entity_id = checkIn.id, operation = "upsert",
            payload_json = Json.encodeToString(checkIn), created_at_epoch_ms = 1L,
        )
        var uploads = 0
        val repository = CheckInRepository({}, { _, _ -> uploads++; SyncResult.Synced }, { 2L }, { "user-1" }, database)

        assertEquals(SyncResult.Synced, repository.syncPending())
        assertEquals(0, uploads)
        assertEquals(1, pendingLocalMutations(database).size)
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
        var uploadedOwner: String? = null
        var uploadedCheckIn: DailyCheckIn? = null
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.NotFound },
            remoteUpsert = { owner, uploaded ->
                uploadedOwner = owner
                uploadedCheckIn = uploaded
                SyncResult.Synced
            },
            nowEpochMs = { 1L },
            currentUserId = { "user-1" },
            database = database,
        )

        assertEquals(SyncResult.Synced, repository.save(checkIn))
        assertEquals(emptyList(), pendingLocalMutations(database))
        assertEquals(checkIn, loadLocalCheckIn(database, checkIn.recordedOn))
        assertEquals("user-1", uploadedOwner)
        assertEquals(checkIn, uploadedCheckIn)
    }

    @Test
    fun signedOutSaveDoesNotCreateOrDeleteQueueData() = runBlocking {
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.NotFound },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 1L },
            currentUserId = { null },
            database = database,
        )

        assertEquals(
            SyncResult.SignedOut,
            repository.save(DailyCheckIn("check-in-1", "2026-09-20")),
        )
        assertEquals(emptyList(), pendingLocalMutations(database))
    }

    @Test
    fun accountOwnershipMismatchRetainsMutationWithoutUploading() = runBlocking {
        saveLocalCheckIn(database, DailyCheckIn("check-in-1", "2026-09-20"), 1L, "user-1")
        var uploads = 0
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.NotFound },
            remoteUpsert = { _, _ -> uploads++; SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { "user-2" },
            database = database,
        )

        assertEquals(SyncResult.OwnershipMismatch, repository.syncPending())
        assertEquals(0, uploads)
        assertEquals(1, pendingLocalMutations(database).size)
    }

    @Test
    fun failedUploadIncrementsAttemptsAndRetainsMutation() = runBlocking {
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.NotFound },
            remoteUpsert = { _, _ -> error("offline") },
            nowEpochMs = { 1L },
            currentUserId = { "user-1" },
            database = database,
        )
        repository.save(DailyCheckIn("check-in-1", "2026-09-20"))

        val mutation = pendingLocalMutations(database).single()
        assertEquals(1L, mutation.attemptCount)
    }

    @Test
    fun pendingLocalDataWinsDuringRefresh() = runBlocking {
        val local = DailyCheckIn("check-in-1", "2026-09-20", soreness = 3)
        saveLocalCheckIn(database, local, 1L, "user-1")
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.Found(DailyCheckIn("remote", "2026-09-20", soreness = 9)) },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { "user-1" },
            database = database,
        )

        assertEquals(RefreshResult.Offline(local), repository.refresh("2026-09-20"))
        assertEquals(local, loadLocalCheckIn(database, "2026-09-20"))
    }

    @Test
    fun remoteRefreshPreservesLocalSameDayId() = runBlocking {
        val local = DailyCheckIn("local-id", "2026-09-20", soreness = 3)
        saveLocalCheckIn(database, local, 1L)
        val remote = DailyCheckIn("remote-id", "2026-09-20", soreness = 9)
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.Found(remote) },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { "user-1" },
            database = database,
        )

        assertEquals(
            RefreshResult.Found(remote.copy(id = "local-id"), local),
            repository.refresh("2026-09-20"),
        )
        assertEquals("local-id", loadLocalCheckIn(database, "2026-09-20")?.id)
    }

    @Test
    fun signedOutRefreshPreservesLocalDataAndStatus() = runBlocking {
        val local = DailyCheckIn("local-id", "2026-09-20", soreness = 3)
        saveLocalCheckIn(database, local, 1L)
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.SignedOut },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { null },
            database = database,
        )

        assertEquals(RefreshResult.SignedOut(local), repository.refresh("2026-09-20"))
        assertEquals(local, loadLocalCheckIn(database, "2026-09-20"))
    }

    @Test
    fun signedOutRefreshWinsOverPendingMutationShortcut() = runBlocking {
        val local = DailyCheckIn("local-id", "2026-09-20", soreness = 3)
        saveLocalCheckIn(database, local, 1L, "user-1")
        val repository = CheckInRepository(
            remoteFetch = { error("must not fetch while signed out") },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { null },
            database = database,
        )

        assertEquals(RefreshResult.SignedOut(local), repository.refresh("2026-09-20"))
        assertEquals(local, loadLocalCheckIn(database, "2026-09-20"))
    }

    @Test
    fun notFoundAndOfflineRefreshesPreserveLocalData() = runBlocking {
        val local = DailyCheckIn("local-id", "2026-09-20", soreness = 3)
        saveLocalCheckIn(database, local, 1L)
        val notFound = CheckInRepository(
            remoteFetch = { RemoteFetchResult.NotFound },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { "user-1" },
            database = database,
        )
        val offline = CheckInRepository(
            remoteFetch = { RemoteFetchResult.Offline },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 2L },
            currentUserId = { "user-1" },
            database = database,
        )

        assertEquals(RefreshResult.NotFound(local), notFound.refresh("2026-09-20"))
        assertEquals(RefreshResult.Offline(local), offline.refresh("2026-09-20"))
        assertEquals(local, loadLocalCheckIn(database, "2026-09-20"))
    }

}
