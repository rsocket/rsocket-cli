package io.rsocket.cli.i9n

import com.google.common.io.CharSource
import io.rsocket.cli.Publishers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class PublishersTest {

  @Test(timeout = 2000)
  @Throws(Exception::class)
  fun testSplitInLinesWithEOF() {
    val list = Publishers.splitInLines(CharSource.wrap("Hello")).collectList().block()

    assertEquals(Arrays.asList("Hello"), list)
  }

  @Test(timeout = 2000)
  @Throws(Exception::class)
  fun testSplitInLinesWithNewLines() {
    val list = Publishers.splitInLines(CharSource.wrap("Hello\nHello1")).collectList().block()
    assertEquals(Arrays.asList("Hello", "Hello1"), list)
  }
}
