package com.reus.nutri

import com.reus.nutri.db.NutriDatabase

sealed interface SyncResult {
    data object Synced : SyncResult
    data object Pending : SyncResult
    data object SignedOut : SyncResult
}

class CheckInRepository(
    private val remoteFetch: suspend (String) -> DailyCheckIn?,
    private val remoteUpsert: suspend (DailyCheckIn) -> SyncResult,
    private val nowEpochMs: () -> Long,
    private val database: NutriDatabase = localDatabase,
) {
    fun local(day: String): DailyCheckIn? = loadLocalCheckIn(database, day)

    suspend fun refresh(day: String): DailyCheckIn? {
        val remote = remoteFetch(day) ?: return local(day)
        replaceLocalCheckIn(database, remote, nowEpochMs())
        return remote
    }

    suspend fun save(checkIn: DailyCheckIn): SyncResult {
        saveLocalCheckIn(database, checkIn, nowEpochMs())
        return syncPending()
    }

    suspend fun syncPending(): SyncResult {
        var result: SyncResult = SyncResult.Synced
        for (mutation in pendingLocalMutations(database)) {
            val checkIn = kotlinx.serialization.json.Json.decodeFromString<DailyCheckIn>(mutation.payloadJson)
            try {
                when (remoteUpsert(checkIn)) {
                    SyncResult.Synced -> database.checkInQueries.deleteMutation(mutation.id)
                    SyncResult.SignedOut -> return SyncResult.SignedOut
                    SyncResult.Pending -> result = SyncResult.Pending
                }
            } catch (_: Exception) {
                database.checkInQueries.incrementMutationAttempts(mutation.id)
                result = SyncResult.Pending
            }
        }
        return if (pendingLocalMutations(database).isEmpty()) SyncResult.Synced else result
    }
}

fun createCheckInRepository(): CheckInRepository = CheckInRepository(
    remoteFetch = { day -> fetchCheckIn(day)?.toDomain() },
    remoteUpsert = { checkIn ->
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) SyncResult.SignedOut else upsertCheckIn(checkIn.toDto(userId))
    },
    nowEpochMs = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
)
