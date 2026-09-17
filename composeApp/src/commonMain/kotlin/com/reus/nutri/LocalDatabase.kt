package com.reus.nutri

import app.cash.sqldelight.db.SqlDriver
import com.reus.nutri.db.NutriDatabase

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
