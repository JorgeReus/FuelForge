package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInRepositoryTest {
    @Test
    fun repositoryPrefillDelegatesToHealthMerge() {
        val repository = CheckInRepository({}, { _, _ -> SyncResult.Synced }, { 0L }, { null })

        assertEquals(72400, repository.prefillFromHealth(
            DailyCheckIn("id", "2026-09-20"),
            HealthDailySnapshot(weightGrams = 72400),
        ).weightGrams)
    }

    @Test
    fun healthSnapshotFillsMissingHealthValues() {
        val checkIn = DailyCheckIn(
            id = "missing",
            recordedOn = "2026-09-20",
            soreness = 6,
            comments = "Manual note",
        )
        val merged = prefillFromHealth(
            checkIn,
            HealthDailySnapshot(weightGrams = 72400, sleepMinutes = 465, activeMinutes = 38),
        )

        assertEquals(72400, merged.weightGrams)
        assertEquals(465, merged.sleepMinutes)
        assertEquals(38, merged.neatMinutes)
        assertEquals(checkIn.soreness, merged.soreness)
        assertEquals(checkIn.comments, merged.comments)
    }

    @Test
    fun healthSnapshotDoesNotOverwriteExistingValuesOrSubjectiveFields() {
        val checkIn = DailyCheckIn(
            id = "existing",
            recordedOn = "2026-09-20",
            weightGrams = 70000,
            sleepMinutes = 420,
            neatMinutes = 25,
            soreness = 6,
            performance = 8,
            motivation = 10,
            hunger = 5,
            fatigue = 4,
            stress = 1,
            sleepQuality = 8,
            comments = "Manual note",
        )
        val merged = prefillFromHealth(
            checkIn,
            HealthDailySnapshot(weightGrams = 72400, sleepMinutes = 465, activeMinutes = 38),
        )

        assertEquals(checkIn, merged)
    }
}
