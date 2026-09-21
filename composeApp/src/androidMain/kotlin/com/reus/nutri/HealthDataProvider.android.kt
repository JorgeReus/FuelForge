package com.reus.nutri

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateRequest
import androidx.health.connect.client.records.ActivityIntensityRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val readPermissions = setOf(
    "android.permission.health.READ_WEIGHT",
    "android.permission.health.READ_SLEEP",
    "android.permission.health.READ_STEPS",
    "android.permission.health.READ_ACTIVITY_INTENSITY",
)

private fun dayRange(day: String, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
    val localDay = LocalDate.parse(day)
    return localDay.atStartOfDay(zone).toInstant() to localDay.plusDays(1).atStartOfDay(zone).toInstant()
}

private fun clientOrNull(context: Context): HealthConnectClient? =
    if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
        HealthConnectClient.getOrCreate(context)
    } else {
        null
    }

actual object HealthDataProvider {
    actual suspend fun readDailySnapshot(day: String): HealthDailySnapshot {
        val client = clientOrNull(androidContext) ?: return HealthDailySnapshot()
        val (start, end) = try {
            dayRange(day)
        } catch (_: RuntimeException) {
            return HealthDailySnapshot()
        }

        return try {
            val range = TimeRangeFilter.between(start, end)
            val weight = runCatching {
                client.readRecords(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = range))
                    .records.maxByOrNull { it.time }
            }.getOrNull()
            val sleepMinutes = runCatching {
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range))
                    .records.sumOf { session ->
                        val sessionStart = maxOf(session.startTime, start)
                        val sessionEnd = minOf(session.endTime, end)
                        ((sessionEnd.toEpochMilli() - sessionStart.toEpochMilli()) / 60_000L).coerceAtLeast(0L)
                    }.takeIf { it > 0 }?.toInt()
            }.getOrNull()
            val aggregate = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            ActivityIntensityRecord.INTENSITY_MINUTES_TOTAL,
                        ),
                        timeRangeFilter = range,
                    ),
                )
            }.getOrNull()
            val steps = aggregate?.get(StepsRecord.COUNT_TOTAL)
            val activeMinutes = aggregate?.get(ActivityIntensityRecord.INTENSITY_MINUTES_TOTAL)?.toInt()
            val hasValue = weight != null || sleepMinutes != null || steps != null || activeMinutes != null
            HealthDailySnapshot(
                weightGrams = weight?.weight?.inKilograms?.times(1_000.0)?.toLong()?.toInt(),
                sleepMinutes = sleepMinutes,
                steps = steps,
                activeMinutes = activeMinutes,
                sourceLabel = "Health Connect".takeIf { hasValue },
            )
        } catch (_: SecurityException) {
            HealthDailySnapshot()
        } catch (_: UnsupportedOperationException) {
            HealthDailySnapshot()
        }
    }
}

@Composable
actual fun HealthImportAction(
    day: String,
    onSnapshot: (HealthDailySnapshot) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { _ ->
        scope.launch {
            onSnapshot(HealthDataProvider.readDailySnapshot(day))
        }
    }
    TextButton(
        onClick = {
            if (context is ComponentActivity && clientOrNull(context) != null) launcher.launch(readPermissions)
            else onSnapshot(HealthDailySnapshot())
        },
    ) {
        Text("Connect health data")
    }
}
