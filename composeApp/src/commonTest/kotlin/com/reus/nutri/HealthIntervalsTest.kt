package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals

class HealthIntervalsTest {
    @Test
    fun overlappingSleepSessionsAreCountedOnce() {
        assertEquals(
            90 * 60 * 1_000L,
            mergedDurationMillis(
                listOf(
                    HealthInterval(0, 60 * 60 * 1_000L),
                    HealthInterval(30 * 60 * 1_000L, 90 * 60 * 1_000L),
                ),
            ),
        )
    }
}
