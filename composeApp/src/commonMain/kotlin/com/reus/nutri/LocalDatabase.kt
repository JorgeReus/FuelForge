package com.reus.nutri

import app.cash.sqldelight.db.SqlDriver
import com.reus.nutri.db.NutriDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

expect fun createDatabaseDriver(): SqlDriver

val localDatabase: NutriDatabase by lazy {
    NutriDatabase(createDatabaseDriver())
}

data class PendingMutation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val attemptCount: Long,
)

@Serializable
internal data class QueuedCheckIn(
    val ownerUserId: String,
    val checkIn: DailyCheckIn,
)

fun saveLocalCheckIn(checkIn: DailyCheckIn, nowEpochMs: Long) {
    saveLocalCheckIn(localDatabase, checkIn, nowEpochMs)
}

fun loadLocalCheckIn(day: String): DailyCheckIn? =
    loadLocalCheckIn(localDatabase, day)

fun pendingLocalMutations(): List<PendingMutation> =
    pendingLocalMutations(localDatabase)

internal fun saveLocalCheckIn(database: NutriDatabase, checkIn: DailyCheckIn, nowEpochMs: Long) {
    saveLocalCheckIn(database, checkIn, nowEpochMs, ownerUserId = null)
}

internal fun saveLocalCheckIn(
    database: NutriDatabase,
    checkIn: DailyCheckIn,
    nowEpochMs: Long,
    ownerUserId: String?,
) {
    val storageDay = ownerUserId?.let { "$it:${checkIn.recordedOn}" } ?: checkIn.recordedOn
    database.transaction {
        val existing = database.checkInQueries.checkInForDay(storageDay).executeAsOneOrNull()
        val persistedId = existing?.id ?: checkIn.id
        val persistedCheckIn = checkIn.copy(id = persistedId)
        val payloadJson = Json.encodeToString(persistedCheckIn)
        val mutationPayload = ownerUserId?.let {
            Json.encodeToString(QueuedCheckIn(it, persistedCheckIn))
        } ?: payloadJson

        database.checkInQueries.deleteMutationsForEntity("patient_check_in", checkIn.id)
        if (existing != null && existing.id != checkIn.id) {
            database.checkInQueries.deleteMutationsForEntity("patient_check_in", existing.id)
        }
        database.checkInQueries.upsertCheckIn(
            id = persistedId,
            recorded_on = storageDay,
            payload_json = payloadJson,
            updated_at_epoch_ms = nowEpochMs,
        )
        database.checkInQueries.enqueueMutation(
            id = "check-in:$persistedId",
            entity_type = "patient_check_in",
            entity_id = persistedId,
            operation = "upsert",
            payload_json = mutationPayload,
            created_at_epoch_ms = nowEpochMs,
        )
    }
}

internal fun loadLocalCheckIn(database: NutriDatabase, day: String, ownerUserId: String? = null): DailyCheckIn? =
    database.checkInQueries.checkInForDay(ownerUserId?.let { "$it:$day" } ?: day).executeAsOneOrNull()?.let {
        Json.decodeFromString<DailyCheckIn>(it.payload_json)
    }

internal fun replaceLocalCheckIn(database: NutriDatabase, checkIn: DailyCheckIn, nowEpochMs: Long, ownerUserId: String? = null) {
    database.transaction {
        database.checkInQueries.upsertCheckIn(
            id = checkIn.id,
            recorded_on = ownerUserId?.let { "$it:${checkIn.recordedOn}" } ?: checkIn.recordedOn,
            payload_json = Json.encodeToString(checkIn),
            updated_at_epoch_ms = nowEpochMs,
        )
    }
}

internal fun pendingLocalMutations(database: NutriDatabase, ownerUserId: String? = null): List<PendingMutation> {
    val mutations = database.checkInQueries.pendingMutations().executeAsList().map {
        PendingMutation(
            id = it.id,
            entityType = it.entity_type,
            entityId = it.entity_id,
            operation = it.operation,
            payloadJson = it.payload_json,
            createdAtEpochMs = it.created_at_epoch_ms,
            attemptCount = it.attempt_count,
        )
    }
    if (ownerUserId != null) mutations.forEach { adoptLegacyMutation(database, it, ownerUserId) }
    return database.checkInQueries.pendingMutations().executeAsList().map {
        PendingMutation(it.id, it.entity_type, it.entity_id, it.operation, it.payload_json, it.created_at_epoch_ms, it.attempt_count)
    }.filter { ownerUserId == null || decodeQueuedCheckIn(it).ownerUserId == ownerUserId }
}

private fun adoptLegacyMutation(database: NutriDatabase, mutation: PendingMutation, ownerUserId: String) {
    val queued = decodeQueuedCheckIn(mutation)
    if (queued.ownerUserId.isNotEmpty() || mutation.entityType != "patient_check_in" || mutation.operation != "upsert") return

    val localRow = database.checkInQueries.checkInForDay(queued.checkIn.recordedOn).executeAsOneOrNull()
    // Legacy rows have no owner. Adopt only an exact device-local row/payload match;
    // anything ambiguous stays queued and can never be uploaded for this account.
    if (localRow?.id != mutation.entityId || localRow.payload_json != mutation.payloadJson) return

    database.checkInQueries.deleteMutation(mutation.id)
    database.checkInQueries.enqueueMutation(
        id = mutation.id,
        entity_type = mutation.entityType,
        entity_id = mutation.entityId,
        operation = mutation.operation,
        payload_json = Json.encodeToString(QueuedCheckIn(ownerUserId, queued.checkIn)),
        created_at_epoch_ms = mutation.createdAtEpochMs,
    )
}

internal fun hasPendingCheckIn(database: NutriDatabase, day: String, ownerUserId: String? = null): Boolean =
    pendingLocalMutations(database, ownerUserId).any { mutation ->
        decodeQueuedCheckIn(mutation).checkIn.recordedOn == day
    }

internal fun decodeQueuedCheckIn(mutation: PendingMutation): QueuedCheckIn {
    return runCatching {
        Json.decodeFromString<QueuedCheckIn>(mutation.payloadJson)
    }.getOrElse {
        QueuedCheckIn("", Json.decodeFromString<DailyCheckIn>(mutation.payloadJson))
    }
}
