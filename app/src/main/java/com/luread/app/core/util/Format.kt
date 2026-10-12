package com.luread.app.core.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Pemformat nilai yang dipakai di seluruh UI. Semua fungsi murni dan dapat diuji. */
object Format {

    private val monthDay = SimpleDateFormat("d MMM", Locale.getDefault())
    private val fullDate = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    private val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun percent(fraction: Float): String {
        val value = (fraction.coerceIn(0f, 1f) * 100f).toInt()
        return "$value%"
    }

    fun bytes(count: Long): String {
        if (count <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = count.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        val pattern = if (value >= 100 || unit == 0) DecimalFormat("0") else DecimalFormat("0.#")
        return "${pattern.format(value)} ${units[unit]}"
    }

    fun duration(minutes: Long): String {
        if (minutes <= 0) return "0m"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours == 0L) "${rest}m" else "${hours}h ${rest}m"
    }

    fun durationSeconds(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    fun date(epochMillis: Long): String = fullDate.format(Date(epochMillis))

    fun shortDate(epochMillis: Long): String = monthDay.format(Date(epochMillis))

    fun time(epochMillis: Long): String = timeOnly.format(Date(epochMillis))

    fun listJoin(items: List<String>, limit: Int = 3): String {
        if (items.isEmpty()) return "—"
        val shown = items.take(limit)
        val suffix = if (items.size > limit) " +${items.size - limit}" else ""
        return shown.joinToString(", ") + suffix
    }

    /** Total menit membaca pada hari kalender yang sama dengan [now]. */
    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
