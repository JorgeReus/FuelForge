package com.reus.nutri

import android.content.Context
import app.cash.sqldelight.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.reus.nutri.db.NutriDatabase

lateinit var androidContext: Context

actual fun createDatabaseDriver(): SqlDriver =
    AndroidSqliteDriver(NutriDatabase.Schema, androidContext, "nutri.db")
