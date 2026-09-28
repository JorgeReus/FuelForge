package com.reus.nutri

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class PatientCheckInDto(
    val id: String,
    val user_id: String,
    val recorded_on: String,
    val weight_grams: Int? = null,
    val soreness: Int? = null,
    val performance: Int? = null,
    val motivation: Int? = null,
    val hunger: Int? = null,
    val fatigue: Int? = null,
    val stress: Int? = null,
    val sleep_minutes: Int? = null,
    val sleep_quality: Int? = null,
    val neat_minutes: Int? = null,
    val comments: String = "",
)
@Serializable
private data class ProfileDto(
    val display_name: String = "",
)

/** Loads the display name for the currently authenticated user. */
suspend fun fetchCurrentDisplayName(): String? {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return null
    return runCatching {
        supabase.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeList<ProfileDto>().firstOrNull()?.display_name?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

@Serializable
data class MealDto(
    val id: String,
    val name: String,
    val description: String = "",
    val meal_type: String,
    val weekday: Int,
    val scheduled_at: String? = null,
    val calories: Int = 0,
    val protein_grams: Int = 0,
    val carbs_grams: Int = 0,
    val fats_grams: Int = 0,
)

@Serializable
data class MealIngredientDto(
    val id: String,
    val meal_id: String,
    val ingredient_name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val grams_quantity: Double? = null,
    val household_portion: String = "",
    val sort_order: Int = 0,
)
fun isoWeekday(isoDate: String): Int {
    val parts = isoDate.split('-').map(String::toInt)
    var year = parts[0]
    val month = parts[1]
    val day = parts[2]
    var adjustedYear = year
    var adjustedMonth = month
    if (adjustedMonth < 3) {
        adjustedYear--
        adjustedMonth += 12
    }
    val century = adjustedYear / 100
    val yearOfCentury = adjustedYear % 100
    val sundayBased = (day + (13 * (adjustedMonth + 1)) / 5 + yearOfCentury +
        yearOfCentury / 4 + century / 4 + 5 * century) % 7
    return ((sundayBased + 5) % 7) + 1
}

suspend fun fetchMealsForWeekday(weekday: Int): List<MealDto> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    return runCatching {
        supabase.from("meals").select {
            filter {
                eq("user_id", userId)
                eq("weekday", weekday)
            }
        }.decodeList<MealDto>().sortedBy { it.scheduled_at.orEmpty() }
    }.getOrDefault(emptyList())
}


suspend fun fetchMealIngredients(mealId: String): List<MealIngredientDto> =
    runCatching {
        supabase.from("meal_ingredients").select {
            filter { eq("meal_id", mealId) }
        }.decodeList<MealIngredientDto>().sortedWith(compareBy({ it.category }, { it.sort_order }, { it.ingredient_name }))
    }.getOrDefault(emptyList())

val supabase = createSupabaseClient(
    supabaseUrl = GeneratedSupabaseConfig.url,
    supabaseKey = GeneratedSupabaseConfig.publishableKey,
) {
    install(Auth)
    install(Postgrest)
}

suspend fun fetchCheckIn(day: String): RemoteFetchResult {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return RemoteFetchResult.SignedOut
    return try {
        val dto = supabase.from("patient_check_ins").select {
            filter {
                eq("user_id", userId)
                eq("recorded_on", day)
            }
        }.decodeList<PatientCheckInDto>().firstOrNull()
        dto?.let { RemoteFetchResult.Found(it.toDomain()) } ?: RemoteFetchResult.NotFound
    } catch (_: Exception) {
        RemoteFetchResult.Offline
    }
}

suspend fun upsertCheckIn(dto: PatientCheckInDto): SyncResult {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return SyncResult.SignedOut
    if (dto.user_id != userId) return SyncResult.SignedOut
    supabase.from("patient_check_ins").upsert(dto) {
        onConflict = "user_id,recorded_on"
    }
    return SyncResult.Synced
}

fun PatientCheckInDto.toDomain(): DailyCheckIn = DailyCheckIn(
    id = id,
    recordedOn = recorded_on,
    weightGrams = weight_grams,
    soreness = soreness,
    performance = performance,
    motivation = motivation,
    hunger = hunger,
    fatigue = fatigue,
    stress = stress,
    sleepMinutes = sleep_minutes,
    sleepQuality = sleep_quality,
    neatMinutes = neat_minutes,
    comments = comments,
)

fun DailyCheckIn.toDto(userId: String): PatientCheckInDto = PatientCheckInDto(
    id = id,
    user_id = userId,
    recorded_on = recordedOn,
    weight_grams = weightGrams,
    soreness = soreness,
    performance = performance,
    motivation = motivation,
    hunger = hunger,
    fatigue = fatigue,
    stress = stress,
    sleep_minutes = sleepMinutes,
    sleep_quality = sleepQuality,
    neat_minutes = neatMinutes,
    comments = comments,
)
