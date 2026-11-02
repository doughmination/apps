package gay.doughmination.mail.ui

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Ported from email/src/web/lib/format.ts.

fun fmtTime(ms: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    return when {
        sameYear && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) ->
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
        sameYear -> android.text.format.DateFormat.format(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM"),
            ms,
        ).toString()
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
    }
}

fun fmtDateTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun fmtSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
}

/** "Ari <ari@x.y>" -> "Ari"; a bare address stays as it is. */
fun displayName(addr: String): String {
    val name = addr.replace(Regex("<[^>]*>"), "").replace("\"", "").trim()
    return name.ifEmpty { addr.replace(Regex("[<>]"), "") }
}

/** "Ari <ari@x.y>" -> "ari@x.y". */
fun bareAddress(addr: String): String = (Regex("<([^<>]+)>").find(addr)?.groupValues?.get(1) ?: addr).trim()

fun isAddress(addr: String): Boolean = Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$").matches(bareAddress(addr))

/** Splits on , ; and newlines, except inside quotes: "Doe, Jane" <j@x.y> stays whole. */
fun splitList(raw: String): List<String> =
    Regex("(?:\"[^\"]*\"?|[^,;\\n\"])+").findAll(raw).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
