package com.reus.nutri

import androidx.compose.runtime.Composable

expect object HealthDataProvider {
    suspend fun readDailySnapshot(day: String): HealthDailySnapshot
}

@Composable
expect fun HealthImportAction(
    day: String,
    onSnapshot: (HealthDailySnapshot) -> Unit,
)
