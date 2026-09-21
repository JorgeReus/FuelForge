package com.reus.nutri

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

actual object HealthDataProvider {
    actual suspend fun readDailySnapshot(day: String): HealthDailySnapshot = HealthDailySnapshot()
}

@Composable
actual fun HealthImportAction(
    day: String,
    onSnapshot: (HealthDailySnapshot) -> Unit,
) {
    val scope = rememberCoroutineScope()
    TextButton(onClick = { scope.launch { onSnapshot(HealthDataProvider.readDailySnapshot(day)) } }) {
        Text("Connect health data")
    }
}
