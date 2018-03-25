package io.rsocket.util

import io.rsocket.Payload
import io.rsocket.cli.i9n.TestOutputHandler
import io.rsocket.cli.LineInputPublishers
import org.junit.Test
import reactor.core.publisher.Flux
import kotlin.test.assertEquals

class LineInputPublisherTest {
  val output = TestOutputHandler()
  val lip = LineInputPublishers(output)

  @Test
  fun multipleInputs() {
    assertEquals(listOf("a", "b", "c"), lip.inputPublisher(listOf("a", "b", "c"), null).toStringList())
  }

  @Test
  fun multipleInputsWithFile() {
    assertEquals(listOf("a", "A: a", "B: b", "c"), lip.inputPublisher(listOf("a", "@src/test/resources/headers.txt", "c"), null).toStringList())
  }

  @Test
  fun multipleInputsWithFailingFile() {
    assertEquals(listOf("a"), lip.inputPublisher(listOf("a", "b", "c", "@NOTHERE"), null).take(1).toStringList())
  }

  private fun Flux<Payload>.toStringList() =
          this.map { it.dataUtf8 }.collectList().block()!!.toList()
}
