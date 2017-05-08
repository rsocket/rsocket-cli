package io.rsocket.cli;

import io.airlift.airline.ParseException;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
    private static final Pattern DURATION_FORMAT = Pattern.compile("(\\d+)(ms|s|m)");

    public static Duration parseShortDuration(String keepalive) {
        Matcher match = DURATION_FORMAT.matcher(keepalive);

        if (!match.matches()) {
            throw new ParseException("Unknown duration format '" + keepalive + "'");
        }

        long amount = Long.valueOf(match.group(1));
        String unit = match.group(2);

        switch (unit) {
            case "ms":
                return Duration.ofMillis(amount);
            case "s":
                return Duration.ofSeconds(amount);
            default:
                return Duration.ofMinutes(amount);
        }
    }
}
