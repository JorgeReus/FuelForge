package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInRepositoryTest {
    @Test
    fun healthSnapshotPrefillsOnlyMissingValues() {
        val manualCheckIn = DailyCheckIn(id = "id", recordedOn = "2026-09-20")
        val repository = CheckInRepository(
            remoteFetch = { RemoteFetchResult.NotFound },
            remoteUpsert = { _, _ -> SyncResult.Synced },
            nowEpochMs = { 1L },
            currentUserId = { null },
        )
        val merged = repository.prefillFromHealth(manualCheckIn, HealthDailySnapshot(weightGrams = 72400))

        assertEquals(72400, merged.weightGrams)
        assertEquals(manualCheckIn.soreness, merged.soreness)
        assertEquals(manualCheckIn.comments, merged.comments)
    }
}
