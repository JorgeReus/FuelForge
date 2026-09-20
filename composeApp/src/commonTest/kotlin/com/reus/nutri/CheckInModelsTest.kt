package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInModelsTest {
    @Test
    fun sorenessOptionsPersistFriendlyScores() {
        assertEquals(0, CheckInMetric.Soreness.choices[0].score)
        assertEquals("Very sore", CheckInMetric.Soreness.choices.last().label)
        assertEquals(9, CheckInMetric.Soreness.choices.last().score)
    }
}
