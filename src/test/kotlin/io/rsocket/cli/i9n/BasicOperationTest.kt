package io.rsocket.cli.i9n

import io.rsocket.AbstractRSocket
import io.rsocket.Closeable
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.cli.Main
import io.rsocket.cli.util.LineInputPublishers
import io.rsocket.exceptions.ApplicationException
import io.rsocket.transport.local.LocalClientTransport
import io.rsocket.transport.local.LocalServerTransport
import io.rsocket.util.DefaultPayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
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
            client!!.close()
        }
        if (server != null) {
            server!!.close().block()
        }
    }

    @Test
    @Throws(Exception::class)
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
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    fun requestResponse() {
        main.requestResponse = true
        main.input = listOf("Hello")

        requestHandler = object : AbstractRSocket() {
            override fun requestResponse(payload: Payload?): Mono<Payload> {
                return Mono.just(reverse(bufferToString(payload)))
            }
        }

        expected.showOutput("olleH")

        run()

        assertEquals(expected, output)
    }

    @Test
    @Throws(Exception::class)
    fun requestResponseFromFile() {
        main.requestResponse = true
        main.input = listOf("@src/test/resources/hello.text")

        requestHandler = object : AbstractRSocket() {
            override fun requestResponse(payload: Payload?): Mono<Payload> {
                return Mono.just(reverse(bufferToString(payload)))
            }
        }

        expected.showOutput("!elif a morf olleH")

        run()

        assertEquals(expected, output)
    }

    @Test
    @Throws(Exception::class)
    fun requestResponseFromMissingFile() {
        main.requestResponse = true
        main.input = listOf("@src/test/resources/goodbye.text")

        requestHandler = object : AbstractRSocket() {
            override fun requestResponse(payload: Payload?): Mono<Payload> {
                return Mono.just(reverse(bufferToString(payload)))
            }
        }

        expected.info("file not found: src/test/resources/goodbye.text")

        run()

        assertEquals(expected, output)
    }

    @Test
    @Throws(Exception::class)
    fun requestResponseError() {
        main.requestResponse = true
        main.input = listOf("Hello")

        requestHandler = object : AbstractRSocket() {
            override fun requestResponse(payload: Payload?): Mono<Payload> {
                return Mono.error(ApplicationException("server failure"))
            }
        }

        expected.error("error from server", ApplicationException("server failure"))

        run()

        assertEquals(expected, output)
    }

    @Test
    @Throws(Exception::class)
    fun stream() {
        main.stream = true
        main.input = listOf("Hello")

        requestHandler = object : AbstractRSocket() {
            override fun requestStream(payload: Payload?): Flux<Payload> {
                val s = bufferToString(payload)
                return Flux.range(1, 3).map { _ -> reverse(s) }
            }
        }

        expected.showOutput("olleH")
        expected.showOutput("olleH")
        expected.showOutput("olleH")

        run()

        assertEquals(expected, output)
    }

    @Test
    @Throws(Exception::class)
    fun streamCompletedByFailure() {
        main.stream = true
        main.input = listOf("Hello")

        requestHandler = object : AbstractRSocket() {
            override fun requestStream(payload: Payload?): Flux<Payload> {
                return Flux.range(1, 3)
                        .map { i -> payload("i " + i!!) }
                        .concatWith(Mono.error(ApplicationException("failed")))
            }
        }

        expected.showOutput("i 1")
        expected.showOutput("i 2")
        expected.showOutput("i 3")
        expected.error("error from server", ApplicationException("failed"))

        run()

        assertEquals(expected, output)
    }

    @Throws(Exception::class)
    private fun run() {
        connect()
        main.run(client).blockLast(Duration.ofSeconds(3))
    }

    companion object {

        private fun bufferToString(payload: Payload?): String {
            return StandardCharsets.UTF_8.decode(payload!!.data).toString()
        }

        fun reverse(s: String): Payload {
            return payload(StringBuilder(s).reverse().toString())
        }

        fun payload(data: String): Payload {
            return DefaultPayload.create(data)
        }
    }
}
