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

private const val readWeightPermission = "android.permission.health.READ_WEIGHT"
private const val readSleepPermission = "android.permission.health.READ_SLEEP"
private const val readStepsPermission = "android.permission.health.READ_STEPS"
private const val readActivityPermission = "android.permission.health.READ_ACTIVITY_INTENSITY"

private val readPermissions = setOf(
    readWeightPermission,
    readSleepPermission,
    readStepsPermission,
    readActivityPermission,
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
        val grantedPermissions = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
        return readDailySnapshot(client, day, grantedPermissions)
    }

    internal suspend fun readDailySnapshot(
        client: HealthConnectClient,
        day: String,
        grantedPermissions: Set<String>,
    ): HealthDailySnapshot {
        val (start, end) = try {
            dayRange(day)
        } catch (_: RuntimeException) {
            return HealthDailySnapshot()
        }

        return try {
            val range = TimeRangeFilter.between(start, end)
            val weight = if (readWeightPermission in grantedPermissions) runCatching {
                client.readRecords(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = range))
                    .records.maxByOrNull { it.time }
            }.getOrNull() else null
            val sleepMinutes = if (readSleepPermission in grantedPermissions) runCatching {
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range))
                    .records.map { session ->
                        val sessionStart = maxOf(session.startTime, start)
                        val sessionEnd = minOf(session.endTime, end)
                        HealthInterval(sessionStart.toEpochMilli(), sessionEnd.toEpochMilli())
                    }.let(::mergedDurationMillis)
                    .div(60_000L)
                    .takeIf { it > 0 }
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
            }.getOrNull() else null
            val steps = if (readStepsPermission in grantedPermissions) runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = range,
                    ),
                )[StepsRecord.COUNT_TOTAL]
            }.getOrNull() else null
            val activeMinutes = if (readActivityPermission in grantedPermissions) runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActivityIntensityRecord.INTENSITY_MINUTES_TOTAL),
                        timeRangeFilter = range,
                    ),
                )[ActivityIntensityRecord.INTENSITY_MINUTES_TOTAL]
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
            }.getOrNull() else null
            val hasValue = weight != null || sleepMinutes != null || steps != null || activeMinutes != null
            HealthDailySnapshot(
                weightGrams = weight?.weight?.inKilograms
                    ?.times(1_000.0)
                    ?.let { kotlin.math.round(it) }
                    ?.toLong()
                    ?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    ?.toInt(),
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
    ) { grantedPermissions ->
        scope.launch {
            val client = (context as? ComponentActivity)?.let(::clientOrNull)
            onSnapshot(
                if (client == null) HealthDailySnapshot()
                else HealthDataProvider.readDailySnapshot(client, day, grantedPermissions),
            )
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
