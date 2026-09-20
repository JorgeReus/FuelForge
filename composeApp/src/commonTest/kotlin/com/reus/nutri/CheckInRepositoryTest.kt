package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInRepositoryTest {
    private val checkIn = DailyCheckIn(id = "check-in-1", recordedOn = "2026-09-20")

    @Test
    fun savingCheckInQueuesAnUpsert() {
        saveLocalCheckIn(checkIn, nowEpochMs = 1L)
        assertEquals("check-in-1", pendingLocalMutations().single().entityId)
        assertEquals("upsert", pendingLocalMutations().single().operation)
    }
}
