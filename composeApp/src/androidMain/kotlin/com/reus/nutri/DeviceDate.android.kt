package com.reus.nutri

import java.time.LocalDate

actual fun currentLocalDate(): String = LocalDate.now().toString()

actual fun localDateDaysAgo(daysAgo: Int): String = LocalDate.now().minusDays(daysAgo.toLong()).toString()
