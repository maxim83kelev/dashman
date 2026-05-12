package cz.kelev.dashman.ui

import java.util.Locale

fun formatRepeatText(repeat: String?): String {
    val value = repeat?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    if (value.isBlank()) return ""

    // Отрезаем суточный диапазон если есть: "every_2_hours|tod:32400:64800"
    val pure = value.substringBefore("|tod:")
    val dayRange = if (value.contains("|tod:")) {
        val parts = value.substringAfter("|tod:").split(":")
        val h1 = (parts.getOrNull(0)?.toIntOrNull() ?: 0) / 3600
        val m1 = ((parts.getOrNull(0)?.toIntOrNull() ?: 0) % 3600) / 60
        val h2 = (parts.getOrNull(1)?.toIntOrNull() ?: 0) / 3600
        val m2 = ((parts.getOrNull(1)?.toIntOrNull() ?: 0) % 3600) / 60
        " %02d:%02d–%02d:%02d".format(h1, m1, h2, m2)
    } else ""

    val base = when (pure) {
        "hourly"   -> "каждый час"
        "daily"    -> "каждый день"
        "weekly"   -> "каждую неделю"
        "monthly"  -> "каждый месяц"
        "weekdays" -> "по будням"
        "weekends" -> "по выходным"
        "every_1_days" -> "каждый день"

        "monday"    -> "каждый понедельник"
        "tuesday"   -> "каждый вторник"
        "wednesday" -> "каждую среду"
        "thursday"  -> "каждый четверг"
        "friday"    -> "каждую пятницу"
        "saturday"  -> "каждую субботу"
        "sunday"    -> "каждое воскресенье"

        else -> pure
            .removePrefix("every_")
            .split("_")
            .let { parts ->
                if (parts.size == 2) {
                    val amount = parts[0]
                    val unit = parts[1]
                    when (unit) {
                        "minutes" -> "каждые $amount мин"
                        "hours"   -> "каждые $amount ч"
                        "days"    -> "каждые $amount дн"
                        "weeks"   -> "каждые $amount нед"
                        "months"  -> "каждые $amount мес"
                        else -> pure
                    }
                } else pure
            }
    }

    return base + dayRange
}

fun formatRepeatRange(repeatFrom: Long?, repeatUntil: Long?): String {
    if (repeatFrom == null && repeatUntil == null) return ""
    val zone = java.time.ZoneId.systemDefault()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("ru"))
    val from = repeatFrom?.let {
        java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(fmt)
    }
    val until = repeatUntil?.let {
        java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(fmt)
    }
    return when {
        from != null && until != null -> "с $from по $until"
        from != null -> "с $from"
        until != null -> "по $until"
        else -> ""
    }
}