package io.rsocket.cli.util

import io.airlift.airline.ParseException
import java.time.Duration
import java.util.regex.Pattern

object TimeUtil {
    private val DURATION_FORMAT = Pattern.compile("(\\d+)(ms|s|m)")

    fun parseShortDuration(keepalive: String): Duration {
        val match = DURATION_FORMAT.matcher(keepalive)

        if (!match.matches()) {
            throw ParseException("Unknown duration format '$keepalive'")
        }

        val amount = java.lang.Long.valueOf(match.group(1))!!
        val unit = match.group(2)

        return when (unit) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            else -> Duration.ofMinutes(amount)
        }
    }
}
