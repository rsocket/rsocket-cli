package io.reactivesocket.cli.i9n;

import io.airlift.airline.ParseException;
import org.junit.Test;

import java.time.Duration;

import static io.reactivesocket.cli.TimeUtil.parseShortDuration;
import static org.junit.Assert.assertEquals;

public class TimeUtilTest {
    @Test
    public void parseMillis() {
        assertEquals(Duration.ofMillis(5), parseShortDuration("5ms"));
    }

    @Test
    public void parseSeconds() {
        assertEquals(Duration.ofSeconds(23), parseShortDuration("23s"));
    }

    @Test
    public void parseMinutes() {
        assertEquals(Duration.ofMinutes(0), parseShortDuration("0m"));
    }

    @Test(expected = ParseException.class)
    public void failOnBadFormat() {
        parseShortDuration("-10 minutes");
    }
}
