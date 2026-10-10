package com.reus.nutri

import android.content.ContextWrapper
import android.content.Intent
import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val readWeightPermission = "android.permission.health.READ_WEIGHT"
private const val readSleepPermission = "android.permission.health.READ_SLEEP"
private const val readStepsPermission = "android.permission.health.READ_STEPS"
private const val readExercisePermission = "android.permission.health.READ_EXERCISE"
private const val readHeartRatePermission = "android.permission.health.READ_HEART_RATE"
private const val readHeightPermission = "android.permission.health.READ_HEIGHT"
private const val readBodyFatPermission = "android.permission.health.READ_BODY_FAT"
private const val readBodyWaterPermission = "android.permission.health.READ_BODY_WATER_MASS"
private const val readBoneMassPermission = "android.permission.health.READ_BONE_MASS"
private const val readLeanBodyMassPermission = "android.permission.health.READ_LEAN_BODY_MASS"
private const val readBasalMetabolicRatePermission = "android.permission.health.READ_BASAL_METABOLIC_RATE"

private val readPermissions = setOf(
    readWeightPermission,
    readSleepPermission,
    readStepsPermission,
    readExercisePermission,
)
private val optionalReadPermissions = setOf(
    readHeartRatePermission,
    readHeightPermission,
    readBodyFatPermission,
    readBodyWaterPermission,
    readBoneMassPermission,
    readLeanBodyMassPermission,
    readBasalMetabolicRatePermission,
)
private val optionalPermissionLabels = mapOf(
    readHeartRatePermission to "heart rate",
    readHeightPermission to "height",
    readBodyFatPermission to "body fat",
    readBodyWaterPermission to "body water",
    readBoneMassPermission to "bone mass",
    readLeanBodyMassPermission to "lean body mass",
    readBasalMetabolicRatePermission to "basal metabolic rate",
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
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readAllRecords(
    client: HealthConnectClient,
    range: TimeRangeFilter,
): List<T> {
    val records = mutableListOf<T>()
    var pageToken: String? = null
    do {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = range,
                pageToken = pageToken,
            ),
        )
        records += response.records
        pageToken = response.pageToken
    } while (pageToken != null)
    return records
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
            val sessionStart = LocalDate.parse(day).minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val sessionRange = TimeRangeFilter.between(sessionStart, end)
            val weight = if (readWeightPermission in grantedPermissions) runCatching {
                readAllRecords<WeightRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            val sleepMinutes = if (readSleepPermission in grantedPermissions) runCatching {
                readAllRecords<SleepSessionRecord>(client, sessionRange)
                    .filter { it.endTime > start && it.endTime <= end }
                    .map { HealthInterval(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
                    .let(::mergedDurationMillis)
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
            val activeMinutes = if (readExercisePermission in grantedPermissions) runCatching {
                readAllRecords<ExerciseSessionRecord>(client, sessionRange).map { session ->
                    val sessionStart = maxOf(session.startTime, start)
                    val sessionEnd = minOf(session.endTime, end)
                    HealthInterval(sessionStart.toEpochMilli(), sessionEnd.toEpochMilli())
                }.let(::mergedDurationMillis)
                    .div(60_000L)
                    .takeIf { it > 0 }
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
            }.getOrNull() else null
            val heartRateBpm = if (readHeartRatePermission in grantedPermissions) runCatching {
                readAllRecords<HeartRateRecord>(client, range)
                    .flatMap { it.samples }
                    .maxByOrNull { it.time }
                    ?.beatsPerMinute
                    ?.coerceIn(1L, Int.MAX_VALUE.toLong())
                    ?.toInt()
            }.getOrNull() else null
            val height = if (readHeightPermission in grantedPermissions) runCatching {
                readAllRecords<HeightRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            val bodyFat = if (readBodyFatPermission in grantedPermissions) runCatching {
                readAllRecords<BodyFatRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            val bodyWater = if (readBodyWaterPermission in grantedPermissions) runCatching {
                readAllRecords<BodyWaterMassRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            val boneMass = if (readBoneMassPermission in grantedPermissions) runCatching {
                readAllRecords<BoneMassRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            val leanBodyMass = if (readLeanBodyMassPermission in grantedPermissions) runCatching {
                readAllRecords<LeanBodyMassRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            val basalMetabolicRate = if (readBasalMetabolicRatePermission in grantedPermissions) runCatching {
                readAllRecords<BasalMetabolicRateRecord>(client, range).maxByOrNull { it.time }
            }.getOrNull() else null
            fun grams(mass: androidx.health.connect.client.units.Mass?): Int? = mass?.inGrams
                ?.takeIf { it > 0 && it < Int.MAX_VALUE }
                ?.let { kotlin.math.round(it).toInt() }
            val heightMillimeters = height?.height?.inMeters?.times(1000.0)
                ?.takeIf { it > 0 && it < Int.MAX_VALUE }
                ?.let { kotlin.math.round(it).toInt() }
            val bodyFatPercentage = bodyFat?.percentage?.value?.times(100.0)
                ?.takeIf { it in 0.0..100.0 }
            val bodyWaterGrams = grams(bodyWater?.mass)
            val boneMassGrams = grams(boneMass?.mass)
            val leanBodyMassGrams = grams(leanBodyMass?.mass)
            val basalMetabolicRateKcalPerDay = basalMetabolicRate?.basalMetabolicRate?.inKilocaloriesPerDay
                ?.takeIf { it > 0 && it < Int.MAX_VALUE }
                ?.let { kotlin.math.round(it).toInt() }
            val hasValue = weight != null || sleepMinutes != null || steps != null || activeMinutes != null || heartRateBpm != null ||
                height != null || bodyFat != null || bodyWater != null || boneMass != null || leanBodyMass != null || basalMetabolicRate != null
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
                heartRateBpm = heartRateBpm,
                heartRatePermissionGranted = readHeartRatePermission in grantedPermissions,
                heightMillimeters = heightMillimeters,
                bodyFatPercentage = bodyFatPercentage,
                bodyWaterGrams = bodyWaterGrams,
                boneMassGrams = boneMassGrams,
                leanBodyMassGrams = leanBodyMassGrams,
                basalMetabolicRateKcalPerDay = basalMetabolicRateKcalPerDay,
                missingOptionalPermissions = (optionalReadPermissions - grantedPermissions)
                    .mapNotNullTo(mutableSetOf()) { optionalPermissionLabels[it] },
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
    val sdkStatus = remember { HealthConnectClient.getSdkStatus(context) }
    val available = sdkStatus == HealthConnectClient.SDK_AVAILABLE
    val scope = rememberCoroutineScope()
    val lifecycle = context.findComponentActivity()?.lifecycle
    var grantedPermissions by remember { mutableStateOf<Set<String>?>(null) }
    val refreshPermissions: () -> Unit = {
        scope.launch {
            grantedPermissions = clientOrNull(context)?.permissionController?.getGrantedPermissions().orEmpty()
        }
    }
    LaunchedEffect(available) {
        if (available) refreshPermissions()
    }
    LaunchedEffect(available, lifecycle) {
        if (!available || lifecycle == null) return@LaunchedEffect
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycle.addObserver(observer)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            lifecycle.removeObserver(observer)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        scope.launch {
            val client = clientOrNull(context)
            val permissions = client?.permissionController?.getGrantedPermissions().orEmpty()
            grantedPermissions = permissions
            onSnapshot(
                if (client == null) HealthDailySnapshot()
                else HealthDataProvider.readDailySnapshot(client, day, permissions),
            )
        }
    }
    val missingPermissions = grantedPermissions?.let { readPermissions - it }.orEmpty()
    val missingOptionalPermissions = grantedPermissions?.let { optionalReadPermissions - it }.orEmpty()
    if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE) {
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { error -> Log.e("FuelForgeHealth", "Unable to open Health Connect listing", error) }
        }) {
            Text("Install Health Connect")
        }
    } else if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { error -> Log.e("FuelForgeHealth", "Unable to open Health Connect update", error) }
        }) {
            Text("Update Health Connect")
        }
    } else if (!available) {
        Text("Health Connect unavailable", color = Amber)
    } else if (missingPermissions.isNotEmpty()) {
        TextButton(onClick = {
            val requestedPermissions = missingPermissions + missingOptionalPermissions
            Log.i("FuelForgeHealth", "Requesting permissions: $requestedPermissions")
            runCatching { launcher.launch(requestedPermissions) }
                .onFailure { error -> Log.e("FuelForgeHealth", "Unable to request permissions", error) }
        }) {
            Text("Grant app permissions")
        }
    } else if (grantedPermissions != null) {
        Row {
            TextButton(onClick = {
                scope.launch {
                    val client = clientOrNull(context)
                    if (client != null) {
                        val permissions = client.permissionController.getGrantedPermissions()
                        grantedPermissions = permissions
                        onSnapshot(HealthDataProvider.readDailySnapshot(client, day, permissions))
                    }
                }
            }) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Text("Sync health data values")
            }
            if (missingOptionalPermissions.isNotEmpty()) {
                TextButton(onClick = { launcher.launch(missingOptionalPermissions) }) {
                    Text("Allow more health data")
                }
            }
        }
    }
}
