package com.reus.nutri

import com.reus.nutri.db.NutriDatabase

sealed interface SyncResult {
    data object Synced : SyncResult
    data object Pending : SyncResult
    data object SignedOut : SyncResult
    data object OwnershipMismatch : SyncResult
}

sealed interface RemoteFetchResult {
    data class Found(val checkIn: DailyCheckIn) : RemoteFetchResult
    data object NotFound : RemoteFetchResult
    data object SignedOut : RemoteFetchResult
    data object Offline : RemoteFetchResult
}

class CheckInRepository(
    private val remoteFetch: suspend (String) -> RemoteFetchResult,
    private val remoteUpsert: suspend (String, DailyCheckIn) -> SyncResult,
    private val nowEpochMs: () -> Long,
    private val currentUserId: () -> String?,
    private val database: NutriDatabase = localDatabase,
) {
    fun local(day: String): DailyCheckIn? = loadLocalCheckIn(database, day)

    suspend fun refresh(day: String): DailyCheckIn? {
        val local = local(day)
        if (hasPendingCheckIn(database, day)) return local
        return when (val result = remoteFetch(day)) {
            is RemoteFetchResult.Found -> {
                val reconciled = result.checkIn.copy(id = local?.id ?: result.checkIn.id)
                replaceLocalCheckIn(database, reconciled, nowEpochMs())
                reconciled
            }
            RemoteFetchResult.NotFound,
            RemoteFetchResult.SignedOut,
            RemoteFetchResult.Offline -> local
        }
    }

    suspend fun save(checkIn: DailyCheckIn): SyncResult {
        val userId = currentUserId() ?: return SyncResult.SignedOut
        saveLocalCheckIn(database, checkIn, nowEpochMs(), userId)
        return syncPending()
    }

    suspend fun syncPending(): SyncResult {
        var result: SyncResult = SyncResult.Synced
        val currentUser = currentUserId() ?: return SyncResult.SignedOut
        for (mutation in pendingLocalMutations(database)) {
            val queued = decodeQueuedCheckIn(mutation)
            if (queued.ownerUserId != currentUser) return SyncResult.OwnershipMismatch
            try {
                when (remoteUpsert(currentUser, queued.checkIn)) {
                    SyncResult.Synced -> database.checkInQueries.deleteMutation(mutation.id)
                    SyncResult.SignedOut -> return SyncResult.SignedOut
                    SyncResult.OwnershipMismatch -> return SyncResult.OwnershipMismatch
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
    remoteFetch = ::fetchCheckIn,
    remoteUpsert = { userId, checkIn ->
        upsertCheckIn(checkIn.toDto(userId))
    },
    nowEpochMs = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    currentUserId = { supabase.auth.currentUserOrNull()?.id },
)
