package com.reus.nutri

data class HealthInterval(val startMillis: Long, val endMillis: Long)

fun mergedDurationMillis(intervals: List<HealthInterval>): Long {
    var mergedStart: Long? = null
    var mergedEnd = 0L
    var total = 0L

    for (interval in intervals.filter { it.endMillis > it.startMillis }.sortedBy { it.startMillis }) {
        if (mergedStart == null) {
            mergedStart = interval.startMillis
            mergedEnd = interval.endMillis
        } else if (interval.startMillis <= mergedEnd) {
            mergedEnd = maxOf(mergedEnd, interval.endMillis)
        } else {
            total += mergedEnd - mergedStart
            mergedStart = interval.startMillis
            mergedEnd = interval.endMillis
        }
    }

    return if (mergedStart == null) total else total + mergedEnd - mergedStart
}
