package com.reus.nutri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class CheckInModelsTest {
    @Test
    fun allMetricsPersistFriendlyScores() {
        val expected = mapOf(
            CheckInMetric.Soreness to listOf("None" to 0, "Light" to 3, "Moderate" to 6, "Very sore" to 9),
            CheckInMetric.Performance to listOf("Struggled" to 2, "Okay" to 5, "Strong" to 8, "Best yet" to 10),
            CheckInMetric.Motivation to listOf("Low" to 2, "Neutral" to 5, "Ready" to 8, "Locked in" to 10),
            CheckInMetric.Hunger to listOf("Not hungry" to 2, "Normal" to 5, "Hungry" to 8, "Ravenous" to 10),
            CheckInMetric.Fatigue to listOf("Fresh" to 1, "A little tired" to 4, "Tired" to 7, "Drained" to 10),
            CheckInMetric.Stress to listOf("Calm" to 1, "Manageable" to 4, "High" to 7, "Overwhelmed" to 10),
            CheckInMetric.SleepQuality to listOf("Poor" to 2, "Fair" to 5, "Good" to 8, "Great" to 10),
        )

        expected.forEach { (metric, choices) ->
            assertEquals(choices, metric.choices.map { it.label to it.score })
        }
    }

    @Test
    fun dailyCheckInRoundTripsThroughJson() {
        val original = DailyCheckIn(
            id = "check-in-1",
            recordedOn = "2026-09-20",
            weightGrams = 81250,
            sleepMinutes = 465,
            neatMinutes = 38,
            soreness = 6,
            performance = 8,
            motivation = 5,
            hunger = 8,
            fatigue = 4,
            stress = 1,
            sleepQuality = 8,
            comments = "Legs feel ready.",
        )

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<DailyCheckIn>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun identifiersAreNonEmptyAndDistinct() {
        val first = newIdentifier()
        val second = newIdentifier()

        assertEquals(false, first.isEmpty())
        assertEquals(false, second.isEmpty())
        assertEquals(false, first == second)
    }
}
