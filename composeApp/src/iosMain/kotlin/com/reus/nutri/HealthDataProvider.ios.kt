package com.reus.nutri

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.NSPredicate
import platform.Foundation.NSSortDescriptor
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryType
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleep
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepCore
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepDeep
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepREM
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepUnspecified
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKWorkoutType
import platform.HealthKit.HKWorkout
import platform.HealthKit.HKSampleSortIdentifierStartDate
import platform.HealthKit.HKUnit
import platform.HealthKit.HKMetricPrefixKilo
import kotlin.coroutines.resume

private const val gramsPerKilogram = 1_000.0
private const val secondsPerMinute = 60.0

@OptIn(ExperimentalForeignApi::class)
private fun dayRange(day: String): Pair<NSDate, NSDate>? {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
        timeZone = NSTimeZone.defaultTimeZone
        dateFormat = "yyyy-MM-dd"
    }
    val start = formatter.dateFromString(day) ?: return null
    return start to NSDate.dateWithTimeIntervalSince1970(start.timeIntervalSince1970 + 86_400.0)
}

@OptIn(ExperimentalForeignApi::class)
private fun predicateForDay(day: String): NSPredicate? {
    val (start, end) = dayRange(day) ?: return null
    return HKQuery.predicateForSamplesWithStartDate(start, endDate = end, options = HKQueryOptionStrictStartDate)
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestReadAuthorization(store: HKHealthStore, readTypes: Set<HKObjectType>) =
    suspendCancellableCoroutine<Boolean> { continuation ->
        store.requestAuthorizationToShareTypes(null, readTypes = readTypes) { success, _ ->
            continuation.resume(success)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun readSamples(
    store: HKHealthStore,
    type: HKSampleType,
    predicate: NSPredicate,
): List<Any> = suspendCancellableCoroutine { continuation ->
    val query = HKSampleQuery(
        sampleType = type,
        predicate = predicate,
        limit = HKObjectQueryNoLimit,
        sortDescriptors = listOf(NSSortDescriptor(key = HKSampleSortIdentifierStartDate, ascending = true)),
    ) { _, samples, _ -> continuation.resume(samples?.filterNotNull() ?: emptyList()) }
    store.executeQuery(query)
    continuation.invokeOnCancellation { store.stopQuery(query) }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun statisticSum(
    store: HKHealthStore,
    type: HKQuantityType,
    predicate: NSPredicate,
): Double? = suspendCancellableCoroutine { continuation ->
    val query = HKStatisticsQuery(type, predicate, HKStatisticsOptionCumulativeSum) { _, statistics, _ ->
        continuation.resume(statistics?.sumQuantity()?.doubleValueForUnit(type.defaultUnit))
    }
    store.executeQuery(query)
    continuation.invokeOnCancellation { store.stopQuery(query) }
}

@OptIn(ExperimentalForeignApi::class)
private fun sleepMinutes(samples: List<Any>): Int? {
    val asleepValues = setOf(
        HKCategoryValueSleepAnalysisAsleep,
        HKCategoryValueSleepAnalysisAsleepCore,
        HKCategoryValueSleepAnalysisAsleepDeep,
        HKCategoryValueSleepAnalysisAsleepREM,
        HKCategoryValueSleepAnalysisAsleepUnspecified,
    ).map { it.toLong() }.toSet()
    val minutes = samples.filterIsInstance<HKCategorySample>()
        .filter { it.value.toLong() in asleepValues }
        .sumOf { it.endDate.timeIntervalSinceDate(it.startDate) / secondsPerMinute }
    return minutes.toInt().takeIf { it > 0 }
}

@OptIn(ExperimentalForeignApi::class)
private fun bodyMassGrams(samples: List<Any>): Int? = samples
    .filterIsInstance<HKQuantitySample>()
    .maxByOrNull { it.startDate.timeIntervalSince1970 }
    ?.quantity
    ?.doubleValueForUnit(HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo))
    ?.let { (it * gramsPerKilogram).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() }

@OptIn(ExperimentalForeignApi::class)
private fun workoutMinutes(samples: List<Any>): Int? {
    val minutes = samples.filterIsInstance<HKWorkout>()
        .sumOf { it.duration / secondsPerMinute }
    return minutes.toInt().takeIf { it > 0 }
}

@OptIn(ExperimentalForeignApi::class)
private fun healthStore(): HKHealthStore? = HKHealthStore().takeIf { HKHealthStore.isHealthDataAvailable() }

@OptIn(ExperimentalForeignApi::class)
actual object HealthDataProvider {
    actual suspend fun readDailySnapshot(day: String): HealthDailySnapshot {
        val store = healthStore() ?: return HealthDailySnapshot()
        val predicate = predicateForDay(day) ?: return HealthDailySnapshot()
        val bodyMass = runCatching {
            val type = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass) ?: return@runCatching null
            bodyMassGrams(readSamples(store, type, predicate))
        }.getOrNull()
        val sleep = runCatching {
            val type = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis) ?: return@runCatching null
            sleepMinutes(readSamples(store, type, predicate))
        }.getOrNull()
        val steps = runCatching {
            val type = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount) ?: return@runCatching null
            statisticSum(store, type, predicate)?.toLong()
        }.getOrNull()
        val activeMinutes = runCatching {
            workoutMinutes(readSamples(store, HKWorkoutType.workoutType(), predicate))
        }.getOrNull()
        val hasValue = bodyMass != null || sleep != null || steps != null || activeMinutes != null
        return HealthDailySnapshot(bodyMass, sleep, steps, activeMinutes, "Apple Health".takeIf { hasValue })
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HealthImportAction(day: String, onSnapshot: (HealthDailySnapshot) -> Unit) {
    val scope = rememberCoroutineScope()
    TextButton(onClick = {
        scope.launch {
            val store = healthStore()
            if (store == null) {
                onSnapshot(HealthDailySnapshot())
                return@launch
            }
            val readTypes = setOfNotNull(
                HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass),
                HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis),
                HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount),
                HKWorkoutType.workoutType(),
            )
            requestReadAuthorization(store, readTypes)
            onSnapshot(HealthDataProvider.readDailySnapshot(day))
        }
    }) {
        Text("Connect health data")
    }
}
