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

val supabase = createSupabaseClient(
    supabaseUrl = "https://wwcemcbyesjqoshksnfv.supabase.co",
    supabaseKey = "sb_publishable_g_fMcwqXouPxY5MASw0Etg_uFTvUvdR",
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
