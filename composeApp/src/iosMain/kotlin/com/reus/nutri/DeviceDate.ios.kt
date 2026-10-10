package com.reus.nutri

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import kotlinx.cinterop.ExperimentalForeignApi

actual fun currentLocalDate(): String = NSDateFormatter().apply {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    timeZone = NSTimeZone.defaultTimeZone
    dateFormat = "yyyy-MM-dd"
}.stringFromDate(NSDate())

@OptIn(ExperimentalForeignApi::class)
actual fun localDateDaysAgo(daysAgo: Int): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
        timeZone = NSTimeZone.defaultTimeZone
        dateFormat = "yyyy-MM-dd"
    }
    val today = formatter.dateFromString(currentLocalDate()) ?: return currentLocalDate()
    val date = NSCalendar.currentCalendar.dateByAddingUnit(
        NSCalendarUnitDay,
        value = -daysAgo.toLong(),
        toDate = today,
        options = 0u,
    ) ?: today
    return formatter.stringFromDate(date)
}
