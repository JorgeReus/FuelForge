package com.reus.nutri

import app.cash.sqldelight.db.SqlDriver
import com.reus.nutri.db.NutriDatabase
import kotlinx.serialization.json.Json

expect fun createDatabaseDriver(): SqlDriver

val localDatabase: NutriDatabase by lazy {
    NutriDatabase(createDatabaseDriver())
}

suspend fun loadLocalTodos(): List<TodoItem> = localDatabase.todoQueries.selectAll().executeAsList().map {
    TodoItem(id = it.id.toIntOrNull() ?: 0, name = it.name)
}

suspend fun saveLocalTodos(items: List<TodoItem>) {
    localDatabase.transaction {
        items.forEach { localDatabase.todoQueries.upsert(it.id.toString(), it.name) }
    }
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

fun saveLocalCheckIn(checkIn: DailyCheckIn, nowEpochMs: Long) {
    val payloadJson = Json.encodeToString(checkIn)
    localDatabase.transaction {
        localDatabase.checkInQueries.upsertCheckIn(
            id = checkIn.id,
            recorded_on = checkIn.recordedOn,
            payload_json = payloadJson,
            updated_at_epoch_ms = nowEpochMs,
        )
        localDatabase.checkInQueries.enqueueMutation(
            id = "check-in:${checkIn.id}",
            entity_type = "patient_check_in",
            entity_id = checkIn.id,
            operation = "upsert",
            payload_json = payloadJson,
            created_at_epoch_ms = nowEpochMs,
        )
    }
}

fun loadLocalCheckIn(day: String): DailyCheckIn? =
    localDatabase.checkInQueries.checkInForDay(day).executeAsOneOrNull()?.let {
        Json.decodeFromString<DailyCheckIn>(it.payload_json)
    }

fun pendingLocalMutations(): List<PendingMutation> =
    localDatabase.checkInQueries.pendingMutations().executeAsList().map {
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
