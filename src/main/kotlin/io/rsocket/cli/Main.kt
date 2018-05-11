/*
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.rsocket.cli

import com.baulsupp.oksocial.output.ConsoleHandler
import com.baulsupp.oksocial.output.OutputHandler
import com.baulsupp.oksocial.output.UsageException
import com.google.common.io.Files
import io.airlift.airline.Arguments
import io.airlift.airline.Command
import io.airlift.airline.Help
import io.airlift.airline.HelpOption
import io.airlift.airline.Option
import io.airlift.airline.ParseException
import io.airlift.airline.SingleCommand
import io.rsocket.AbstractRSocket
import io.rsocket.Closeable
import io.rsocket.ConnectionSetupPayload
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.cli.Main.Companion.NAME
import io.rsocket.transport.TransportHeaderAware
import io.rsocket.uri.UriTransportRegistry
import io.rsocket.util.DefaultPayload
import io.rsocket.util.EmptyPayload
import kotlinx.coroutines.experimental.reactive.awaitFirst
import kotlinx.coroutines.experimental.reactive.awaitFirstOrNull
import kotlinx.coroutines.experimental.reactor.mono
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.system.exitProcess

/**
 * Simple command line tool to make a RSocket connection and send/receive elements.
 *
 * Currently limited in features, only supports a text/line based approach.
 */
@Command(name = NAME, description = "CLI for RSocket.")
class Main : HelpOption() {

  @Option(name = ["-H", "--header"], description = "Custom header to pass to server")
  var headers: List<String>? = null

  @Option(name = ["-T", "--transport-header"], description = "Custom header to pass to the transport")
  var transportHeader: List<String>? = null

  @Option(name = ["--stream"], description = "Request Stream")
  var stream: Boolean = false

  @Option(name = ["--request"], description = "Request Response")
  var requestResponse: Boolean = false

  @Option(name = ["--fnf"], description = "Fire and Forget")
  var fireAndForget: Boolean = false

  @Option(name = ["--channel"], description = "Channel")
  var channel: Boolean = false

  @Option(name = ["--metadataPush"], description = "Metadata Push")
  var metadataPush: Boolean = false

  @Option(name = ["--server"], description = "Start server instead of client")
  var serverMode: Boolean = false

  @Option(name = ["-i", "--input"], description = "String input, '-' (STDIN) or @path/to/file")
  var input: List<String>? = null

  @Option(name = ["-m", "--metadata"], description = "Metadata input string input or @path/to/file")
  var metadata: String? = null

  @Option(name = ["--metadataFormat"], description = "Metadata Format", allowedValues = ["json", "cbor", "mime-type"])
  var metadataFormat = "json"

  @Option(name = ["--dataFormat"], description = "Data Format", allowedValues = ["json", "cbor", "mime-type"])
  var dataFormat = "binary"

  @Option(name = ["--setup"], description = "String input or @path/to/file for setup metadata")
  var setup: String? = null

  @Option(name = ["--debug"], description = "Debug Output")
  var debug: Boolean = false

  @Option(name = ["--ops"], description = "Operation Count")
  var operations = 1

  @Option(name = ["--timeout"], description = "Timeout in seconds")
  var timeout: Long? = null

  @Option(name = ["--keepalive"], description = "Keepalive period")
  var keepalive: String? = null

  @Option(name = ["--requestn", "-r"], description = "Request N credits")
  var requestN = Integer.MAX_VALUE

  @Arguments(title = "target", description = "Endpoint URL", required = true)
  var arguments: List<String> = ArrayList()

  lateinit var client: RSocket

  lateinit var outputHandler: OutputHandler<Any>

  lateinit var inputPublisher: InputPublisher

  lateinit var server: Closeable

  suspend fun run() {
    configureLogging(debug)

    if (!this::outputHandler.isInitialized) {
      outputHandler = ConsoleHandler.instance()
    }

    if (!this::inputPublisher.isInitialized) {
      inputPublisher = LineInputPublishers(outputHandler)
    }

    try {
      val uri = arguments[0]

      if (serverMode) {
        server = buildServer(uri)

        server.onClose().awaitFirstOrNull()
      } else {
        if (!this::client.isInitialized) {
          client = buildClient(uri)
        }

        val run = run(client)

        if (timeout != null) {
          withTimeout(timeout!!, TimeUnit.SECONDS) {
            run.then().awaitFirstOrNull()
          }
        } else {
          run.then().awaitFirstOrNull()
        }
      }
    } catch (e: Exception) {
      outputHandler.showError("error", e)
    }
  }

  suspend fun buildServer(uri: String): Closeable {
    val transport = RSocketFactory.receive()
      .acceptor(this::createServerRequestHandler)
      .transport(UriTransportRegistry.serverForUri(uri))
    return transport.start().awaitFirst()
  }

  suspend fun buildClient(uri: String): RSocket {
    val clientRSocketFactory = RSocketFactory.connect()

    if (keepalive != null) {
      clientRSocketFactory.keepAliveTickPeriod(parseShortDuration(keepalive!!))
    }
    clientRSocketFactory.errorConsumer { t ->
      runBlocking {
        outputHandler.showError("client error", t)
      }
    }
    clientRSocketFactory.metadataMimeType(standardMimeType(metadataFormat))
    clientRSocketFactory.dataMimeType(standardMimeType(dataFormat))
    if (setup != null) {
      clientRSocketFactory.setupPayload(parseSetupPayload())
    }

    val clientTransport = UriTransportRegistry.clientForUri(uri)

    if (transportHeader != null && clientTransport is TransportHeaderAware) {
      clientTransport.setTransportHeaders({ headerMap(transportHeader) })
    }

    clientRSocketFactory.acceptor(this::createClientRequestHandler)

    return clientRSocketFactory.transport(clientTransport).start().awaitFirst()
  }

  private fun createClientRequestHandler(socket: RSocket): RSocket = createResponder()

  private fun standardMimeType(dataFormat: String?): String = when (dataFormat) {
    null -> "application/json"
    "json" -> "application/json"
    "cbor" -> "application/cbor"
    "binary" -> "application/binary"
    "text" -> "text/plain"
    else -> dataFormat
  }

  private fun parseSetupPayload(): Payload = when {
    setup == null -> EmptyPayload.INSTANCE
    setup!!.startsWith("@") -> DefaultPayload.create(Files.asCharSource(expectedFile(setup!!.substring(1)), StandardCharsets.UTF_8).read())
    else -> DefaultPayload.create(setup!!)
  }

  private fun createServerRequestHandler(setupPayload: ConnectionSetupPayload, socket: RSocket): Mono<RSocket> {
    LoggerFactory.getLogger(Main::class.java).debug("setup payload $setupPayload")

    runAllOperations(socket).subscribe()

    return Mono.just(createResponder())
  }

  private fun createResponder(): AbstractRSocket {
    return object : AbstractRSocket() {
      override fun fireAndForget(payload: Payload): Mono<Void> = mono {
        outputHandler.showOutput(payload.dataUtf8)
      }.then()

      override fun requestResponse(payload: Payload): Mono<Payload> = handleIncomingPayload(payload).single()

      override fun requestStream(payload: Payload): Flux<Payload> = handleIncomingPayload(payload)

      override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
        Flux.from(payloads).takeN(requestN)
          .onNext { outputHandler.showOutput(it.dataUtf8) }
          .onError { outputHandler.showError("channel error", it) }.subscribe()
        return inputPublisherX()
      }

      override fun metadataPush(payload: Payload): Mono<Void> = mono {
        outputHandler.showOutput(payload.metadataUtf8)
      }.then()
    }
  }

  private fun handleIncomingPayload(payload: Payload): Flux<Payload> = mono {
    outputHandler.showOutput(payload.dataUtf8)
  }.thenMany(inputPublisherX())

  fun getInputFromSource(source: String?, nullHandler: Supplier<String>): String =
    when (source) {
      null -> nullHandler.get()
      else -> stringValue(source)
    }

  fun buildMetadata(): ByteArray? = when {
    this.metadata != null -> {
      if (this.headers != null) {
        throw UsageException("Can't specify headers and metadata")
      }

      getInputFromSource(this.metadata, Supplier { ""; }).toByteArray(StandardCharsets.UTF_8)
    }
    this.headers != null -> encodeMetadataMap(headerMap(headers), standardMimeType(metadataFormat))
    else -> ByteArray(0)
  }

  private fun inputPublisherX(): Flux<Payload> {
    return inputPublisher.inputPublisher(input ?: listOf("-"), buildMetadata())
  }

  private fun singleInputPayload(): Payload {
    return inputPublisher.singleInputPayload(input ?: listOf("-"), buildMetadata())
  }

  suspend fun run(client: RSocket): Flux<Void> = try {
    runAllOperations(client)
  } catch (e: Exception) {
    outputHandler.showError("error", e)
    Flux.empty()
  }

  private fun runAllOperations(client: RSocket): Flux<Void> =
    Flux.range(0, operations).flatMap { runSingleOperation(client) }

  private fun runSingleOperation(client: RSocket): Flux<Void> = try {
    when {
      fireAndForget -> client.fireAndForget(singleInputPayload()).thenMany(Flux.empty())
      metadataPush -> client.metadataPush(singleInputPayload()).thenMany(Flux.empty())
      requestResponse -> client.requestResponse(singleInputPayload()).flux()
      stream -> client.requestStream(singleInputPayload())
      channel -> client.requestChannel(inputPublisherX())
      else -> Flux.never()
    }
      .takeN(requestN)
      .map { it.dataUtf8 }
      .onNext { outputHandler.showOutput(it) }
      .onError { outputHandler.showError("error from server", it) }
      .onErrorResume { Flux.empty() }
      .then().flux()
  } catch (ex: Exception) {
    mono {
      outputHandler.showError("error before query", ex)
    }.then().flux()
  }

  companion object {
    const val NAME = "reactivesocket-cli"

    private fun fromArgs(vararg args: String): Main {
      val cmd = SingleCommand.singleCommand<Main>(Main::class.java)
      return try {
        cmd.parse(*args)
      } catch (e: ParseException) {
        System.err.println(e.message)
        Help.help(cmd.commandMetadata)
        exitProcess(-1)
      }
    }

    @JvmStatic
    fun main(vararg args: String) {
      runBlocking {
        fromArgs(*args).run()
      }
    }
  }
}
