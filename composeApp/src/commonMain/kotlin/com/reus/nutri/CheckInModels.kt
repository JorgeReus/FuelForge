package com.reus.nutri

import kotlinx.serialization.Serializable

@Serializable
data class CheckInChoice(
    val label: String,
    val score: Int,
)

enum class CheckInMetric(
    val title: String,
    val choices: List<CheckInChoice>,
) {
    Soreness(
        "Soreness",
        listOf(
            CheckInChoice("None", 0),
            CheckInChoice("Light", 3),
            CheckInChoice("Moderate", 6),
            CheckInChoice("Very sore", 9),
        ),
    ),
    Performance(
        "Performance",
        listOf(
            CheckInChoice("Struggled", 2),
            CheckInChoice("Okay", 5),
            CheckInChoice("Strong", 8),
            CheckInChoice("Best yet", 10),
        ),
    ),
    Motivation(
        "Motivation",
        listOf(
            CheckInChoice("Low", 2),
            CheckInChoice("Neutral", 5),
            CheckInChoice("Ready", 8),
            CheckInChoice("Locked in", 10),
        ),
    ),
    Hunger(
        "Hunger",
        listOf(
            CheckInChoice("Not hungry", 2),
            CheckInChoice("Normal", 5),
            CheckInChoice("Hungry", 8),
            CheckInChoice("Ravenous", 10),
        ),
    ),
    Fatigue(
        "Fatigue",
        listOf(
            CheckInChoice("Fresh", 1),
            CheckInChoice("A little tired", 4),
            CheckInChoice("Tired", 7),
            CheckInChoice("Drained", 10),
        ),
    ),
    Stress(
        "Stress",
        listOf(
            CheckInChoice("Calm", 1),
            CheckInChoice("Manageable", 4),
            CheckInChoice("High", 7),
            CheckInChoice("Overwhelmed", 10),
        ),
    ),
    SleepQuality(
        "Sleep quality",
        listOf(
            CheckInChoice("Poor", 2),
            CheckInChoice("Fair", 5),
            CheckInChoice("Good", 8),
            CheckInChoice("Great", 10),
        ),
    ),
}

@Serializable
data class DailyCheckIn(
    val id: String,
    val recordedOn: String,
    val weightGrams: Int? = null,
    val sleepMinutes: Int? = null,
    val neatMinutes: Int? = null,
    val soreness: Int? = null,
    val performance: Int? = null,
    val motivation: Int? = null,
    val hunger: Int? = null,
    val fatigue: Int? = null,
    val stress: Int? = null,
    val sleepQuality: Int? = null,
    val comments: String = "",
)

data class HealthDailySnapshot(
    val weightGrams: Int? = null,
    val sleepMinutes: Int? = null,
    val steps: Long? = null,
    val activeMinutes: Int? = null,
    val sourceLabel: String? = null,
)
