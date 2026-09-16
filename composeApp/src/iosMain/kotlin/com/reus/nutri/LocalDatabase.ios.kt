package com.reus.nutri

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.reus.nutri.db.NutriDatabase

actual fun createDatabaseDriver(): SqlDriver = NativeSqliteDriver(NutriDatabase.Schema, "nutri.db")
