package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInSheetTest {
    @Test
    fun selectingVerySoreProducesScoreNine() {
        val form = CheckInFormState(DailyCheckIn(id = "id", recordedOn = "2026-09-20"))
        form.select(CheckInMetric.Soreness, CheckInMetric.Soreness.choices.last())

        assertEquals(9, form.toCheckIn().soreness)
    }

    @Test
    fun formConvertsWeightAndSleepBeforeSaving() {
        val form = CheckInFormState(DailyCheckIn(id = "id", recordedOn = "2026-09-20"))
        form.weightKg = "81.25"
        form.sleepHours = "7"
        form.sleepMinutes = "45"

        assertEquals(81250, form.toCheckIn().weightGrams)
        assertEquals(465, form.toCheckIn().sleepMinutes)
    }

    @Test
    fun existingPatientValuesWinOverHealthDefaults() {
        val initial = DailyCheckIn(id = "id", recordedOn = "2026-09-20", weightGrams = 80000, sleepMinutes = 420)
        val merged = mergeHealthDefaults(initial, HealthDailySnapshot(weightGrams = 81250, sleepMinutes = 465))

        assertEquals(80000, merged.weightGrams)
        assertEquals(420, merged.sleepMinutes)
    }

    @Test
    fun selectingTheSameChoiceClearsIt() {
        val form = CheckInFormState(DailyCheckIn(id = "id", recordedOn = "2026-09-20"))
        val choice = CheckInMetric.Soreness.choices.last()
        form.toggle(CheckInMetric.Soreness, choice)
        form.toggle(CheckInMetric.Soreness, choice)

        assertEquals(null, form.toCheckIn().soreness)
    }
}
