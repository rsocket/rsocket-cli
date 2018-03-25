package io.rsocket.cli.i9n

import io.rsocket.AbstractRSocket
import io.rsocket.Closeable
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.cli.Main
import io.rsocket.cli.LineInputPublishers
import io.rsocket.exceptions.ApplicationException
import io.rsocket.transport.local.LocalClientTransport
import io.rsocket.transport.local.LocalServerTransport
import io.rsocket.util.DefaultPayload
import kotlinx.coroutines.experimental.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

class BasicOperationTest {
  private val main = Main()
  private val output = TestOutputHandler()
  private var client: RSocket? = null
  private var server: Closeable? = null

  private val expected = TestOutputHandler()

  private var requestHandler: RSocket = object : AbstractRSocket() {
  }

  private var testName: String? = null

  @get:Rule
  var watcher: TestRule = object : TestWatcher() {
    override fun starting(description: Description?) {
      testName = description!!.methodName
    }
  }

  private fun connect() {
    main.outputHandler = output
    main.inputPublisher = LineInputPublishers(output)

    server = RSocketFactory.receive()
      .acceptor { _, _ -> Mono.just(requestHandler) }
      .transport(LocalServerTransport.create("test-local-server-" + testName!!))
      .start()
      .block()

    client = RSocketFactory.connect()
      .transport(LocalClientTransport.create("test-local-server-" + testName!!))
      .start()
      .block()
  }

  @After
  fun shutdown() {
    if (client != null) {
      client!!.dispose()
    }
    if (server != null) {
      server!!.dispose()
    }
  }

  @Test
  fun metadataPush() {
    main.metadataPush = true
    main.input = listOf("Hello")

    requestHandler = object : AbstractRSocket() {
      override fun metadataPush(payload: Payload?): Mono<Void> {
        return Mono.empty()
      }
    }

    run()

    assertEquals(expected, output)
  }

  @Test
  fun fireAndForget() {
    main.fireAndForget = true
    main.input = listOf("Hello")

    requestHandler = object : AbstractRSocket() {
      override fun fireAndForget(payload: Payload?): Mono<Void> {
        return Mono.empty()
      }
    }

    run()

    assertEquals(expected, output)
  }

  @Test
  fun requestResponse() {
    main.requestResponse = true
    main.input = listOf("Hello")

    requestHandler = object : AbstractRSocket() {
      override fun requestResponse(payload: Payload): Mono<Payload> {
        return Mono.just(DefaultPayload.create(payload.dataUtf8.reversed()))
      }
    }

    expectedShowOutput("olleH")

    run()

    assertEquals(expected, output)
  }

  @Test
  fun requestResponseFromFile() {
    main.requestResponse = true
    main.input = listOf("@src/test/resources/hello.text")

    requestHandler = object : AbstractRSocket() {
      override fun requestResponse(payload: Payload): Mono<Payload> {
        return Mono.just(DefaultPayload.create(payload.dataUtf8.reversed()))
      }
    }

    expectedShowOutput("!elif a morf olleH")

    run()

    assertEquals(expected, output)
  }

  @Test
  fun requestResponseFromMissingFile() {
    main.requestResponse = true
    main.input = listOf("@src/test/resources/goodbye.text")

    requestHandler = object : AbstractRSocket() {
      override fun requestResponse(payload: Payload): Mono<Payload> {
        return Mono.just(DefaultPayload.create(payload.dataUtf8.reversed()))
      }
    }

    expected.info("file not found: src/test/resources/goodbye.text")

    run()

    assertEquals(expected, output)
  }

  @Test
  fun requestResponseError() {
    main.requestResponse = true
    main.input = listOf("Hello")

    requestHandler = object : AbstractRSocket() {
      override fun requestResponse(payload: Payload?): Mono<Payload> {
        return Mono.error(ApplicationException("server failure"))
      }
    }

    expectedShowError("error from server", ApplicationException("server failure"))

    run()

    assertEquals(expected, output)
  }

  @Test
  fun stream() {
    main.stream = true
    main.input = listOf("Hello")

    requestHandler = object : AbstractRSocket() {
      override fun requestStream(payload: Payload): Flux<Payload> {
        val s = payload.dataUtf8
        val flux = Flux.range(1, 3).map { _ -> DefaultPayload.create(s.reversed()) }
        return flux
      }
    }

    expectedShowOutput("olleH")
    expectedShowOutput("olleH")
    expectedShowOutput("olleH")

    run()

    assertEquals(expected, output)
  }

  @Test
  @Ignore
  fun streamCompletedByFailure() {
    main.stream = true
    main.input = listOf("Hello")

    requestHandler = object : AbstractRSocket() {
      override fun requestStream(payload: Payload): Flux<Payload> {
        val flux = Flux.range(1, 3)
          .map { DefaultPayload.create("i $it") }
          .concatWith(Mono.error(ApplicationException("failed")))
        return flux
      }
    }

    expectedShowOutput("i 1")
    expectedShowOutput("i 2")
    expectedShowOutput("i 3")
    expectedShowError("error from server", ApplicationException("failed"))

    run()

    assertEquals(expected, output)
  }

  private fun expectedShowError(msg: String, e: Throwable) {
    runBlocking {
      expected.showError(msg, e)
    }
  }

  private fun expectedShowOutput(s: String) {
    runBlocking {
      expected.showOutput(s)
    }
  }

  private fun run() {
    connect()
    main.run(client!!).blockLast(Duration.ofSeconds(3))
  }
}
