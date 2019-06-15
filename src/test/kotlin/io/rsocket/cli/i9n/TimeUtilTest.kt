package io.rsocket.cli.i9n

import com.baulsupp.oksocial.output.UsageException
import io.rsocket.cli.parseShortDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimeUtilTest {
  @Test
  fun parseMillis() {
    assertEquals(Duration.ofMillis(5), parseShortDuration("5ms"))
  }

  @Test
  fun parseSeconds() {
    assertEquals(Duration.ofSeconds(23), parseShortDuration("23s"))
  }

  @Test
  fun parseMinutes() {
    assertEquals(Duration.ofMinutes(0), parseShortDuration("0m"))
  }

  @Test(expected = UsageException::class)
  fun failOnBadFormat() {
    parseShortDuration("-10 minutes")
  }
}
