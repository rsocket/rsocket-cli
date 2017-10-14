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

import com.google.common.io.Files
import io.airlift.airline.*
import io.rsocket.*
import io.rsocket.cli.Main.Companion.NAME
import io.rsocket.cli.util.*
import io.rsocket.cli.util.FileUtil.expectedFile
import io.rsocket.cli.util.HeaderUtil.headerMap
import io.rsocket.cli.util.TimeUtil.parseShortDuration
import io.rsocket.transport.TransportHeaderAware
import io.rsocket.uri.UriTransportRegistry
import io.rsocket.util.PayloadImpl
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.function.Supplier
import kotlin.system.exitProcess

/**
 * Simple command line tool to make a RSocket connection and send/receive elements.
 *
 * Currently limited in features, only supports a text/line based approach.
 */
@Command(name = NAME, description = "CLI for RSocket.")
class Main: HelpOption() {

  @Option(name = arrayOf("-H", "--header"), description = "Custom header to pass to server")
  var headers: List<String>? = null

  @Option(name = arrayOf("-T", "--transport-header"), description = "Custom header to pass to the transport")
  var transportHeader: List<String>? = null

  @Option(name = arrayOf("--stream"), description = "Request Stream")
  var stream: Boolean = false

  @Option(name = arrayOf("--request"), description = "Request Response")
  var requestResponse: Boolean = false

  @Option(name = arrayOf("--fnf"), description = "Fire and Forget")
  var fireAndForget: Boolean = false

  @Option(name = arrayOf("--channel"), description = "Channel")
  var channel: Boolean = false

  @Option(name = arrayOf("--metadataPush"), description = "Metadata Push")
  var metadataPush: Boolean = false

  @Option(name = arrayOf("--server"), description = "Start server instead of client")
  var serverMode: Boolean = false

  @Option(name = arrayOf("-i", "--input"), description = "String input, '-' (STDIN) or @path/to/file")
  var input: List<String>? = null

  @Option(name = arrayOf("-m", "--metadata"), description = "Metadata input string input or @path/to/file")
  var metadata: String? = null

  @Option(name = arrayOf("--metadataFormat"), description = "Metadata Format", allowedValues = arrayOf("json", "cbor", "mime-type"))
  var metadataFormat = "json"

  @Option(name = arrayOf("--dataFormat"), description = "Data Format", allowedValues = arrayOf("json", "cbor", "mime-type"))
  var dataFormat = "binary"

  @Option(name = arrayOf("--setup"), description = "String input or @path/to/file for setup metadata")
  var setup: String? = null

  @Option(name = arrayOf("--debug"), description = "Debug Output")
  var debug: Boolean = false

  @Option(name = arrayOf("--ops"), description = "Operation Count")
  var operations = 1

  @Option(name = arrayOf("--timeout"), description = "Timeout in seconds")
  var timeout: Long? = null

  @Option(name = arrayOf("--keepalive"), description = "Keepalive period")
  var keepalive: String? = null

  @Option(name = arrayOf("--requestn", "-r"), description = "Request N credits")
  var requestN = Integer.MAX_VALUE

  @Arguments(title = "target", description = "Endpoint URL", required = true)
  var arguments: List<String> = ArrayList()

  var client: RSocket? = null

  var outputHandler: OutputHandler? = null

  var inputPublisher: InputPublisher? = null

  private var server: Closeable? = null

  fun run() {
    LoggingUtil.configureLogging(debug)

    if (outputHandler == null) {
      outputHandler = ConsoleOutputHandler()
    }

    if (inputPublisher == null) {
      inputPublisher = LineInputPublishers(outputHandler!!)
    }

    try {
      val uri = arguments[0]

      if (serverMode) {
        val transport = RSocketFactory.receive()
            .acceptor(this::createServerRequestHandler)
            .transport(UriTransportRegistry.serverForUri(uri))
        server = transport.start().block()

        server!!.onClose().block()
      } else {
        val clientRSocketFactory = RSocketFactory.connect()

        if (keepalive != null) {
          clientRSocketFactory.keepAliveTickPeriod(parseShortDuration(keepalive!!))
        }
        clientRSocketFactory.errorConsumer { t -> outputHandler!!.error("client error", t) }
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

        client = clientRSocketFactory.transport(clientTransport).start().block()

        val run = run(client)

        if (timeout != null) {
          run.blockLast(Duration.ofSeconds(timeout!!))
        } else {
          run.blockLast()
        }
      }
    } catch (e: Exception) {
      handleError(e)
    }
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
    setup == null -> PayloadImpl.EMPTY
    setup!!.startsWith("@") -> PayloadImpl(Files.asCharSource(expectedFile(setup!!.substring(1)), StandardCharsets.UTF_8).read())
    else -> PayloadImpl(setup)
  }

  private fun createServerRequestHandler(setupPayload: ConnectionSetupPayload, socket: RSocket): Mono<RSocket> {
    LoggerFactory.getLogger(Main::class.java).debug("setup payload " + setupPayload)

    runAllOperations(socket).subscribe()

    return Mono.just(createResponder())
  }

  private fun createResponder(): AbstractRSocket {
    return object : AbstractRSocket() {
      override fun fireAndForget(payload: Payload): Mono<Void> {
        showPayload(payload)
        return Mono.empty()
      }

      override fun requestResponse(payload: Payload): Mono<Payload> {
        return handleIncomingPayload(payload).single()
      }

      override fun requestStream(payload: Payload): Flux<Payload> {
        return handleIncomingPayload(payload)
      }

      override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
        Flux.from(payloads).takeN(requestN)
                .subscribe({ p -> showPayload(p) }, { e -> outputHandler!!.error("channel error", e) })
        return inputPublisher()
      }

      override fun metadataPush(payload: Payload?): Mono<Void> {
        outputHandler!!.showOutput(toUtf8String(payload!!.metadata))
        return Mono.empty()
      }
    }
  }

  private fun handleIncomingPayload(payload: Payload): Flux<Payload> {
    showPayload(payload)
    return inputPublisher()
  }

  fun getInputFromSource(source: String?, nullHandler: Supplier<String>): String =
      when (source) {
        null -> nullHandler.get()
        else -> HeaderUtil.stringValue(source)
      }

  fun buildMetadata(): ByteArray? = when {
    this.metadata != null -> {
      if (this.headers != null) {
        throw UsageException("Can't specify headers and metadata")
      }

      getInputFromSource(this.metadata, Supplier { ""; }).toByteArray(StandardCharsets.UTF_8)
    }
    this.headers != null -> MetadataUtil.encodeMetadataMap(HeaderUtil.headerMap(headers), standardMimeType(metadataFormat))
    else -> ByteArray(0)
  }

  private fun inputPublisher(): Flux<Payload> {
    return inputPublisher!!.inputPublisher(input ?: listOf("-"), buildMetadata())
  }

  private fun singleInputPayload(): Payload {
    return inputPublisher!!.singleInputPayload(input ?: listOf("-"), buildMetadata())
  }

  private fun showPayload(payload: Payload) {
    outputHandler!!.showOutput(toUtf8String(payload.data))
  }

  private fun toUtf8String(data: ByteBuffer): String =
      StandardCharsets.UTF_8.decode(data).toString()

  fun run(client: RSocket?): Flux<Void> = try {
    runAllOperations(client)
  } catch (e: Exception) {
    handleError(e)
    Flux.empty()
  }

  private fun handleError(e: Throwable) {
    outputHandler!!.error("error", e)
  }

  private fun runAllOperations(client: RSocket?): Flux<Void> =
      Flux.range(0, operations).flatMap { runSingleOperation(client) }

  private fun runSingleOperation(client: RSocket?): Flux<Void> = try {
    when {
      fireAndForget -> client!!.fireAndForget(singleInputPayload()).thenMany(Flux.empty())
      metadataPush -> client!!.metadataPush(singleInputPayload()).thenMany(Flux.empty())
      requestResponse -> client!!.requestResponse(singleInputPayload()).flux()
      stream -> client!!.requestStream(singleInputPayload())
      channel -> client!!.requestChannel(inputPublisher())
      else -> Flux.never()
    }
        .takeN(requestN)
        .map({ it.data })
        .map({ this.toUtf8String(it) })
        .doOnNext({ outputHandler!!.showOutput(it) })
        .doOnError { e -> outputHandler!!.error("error from server", e) }
        .onErrorResume { Flux.empty() }
        .thenMany(Flux.empty())
  } catch (ex: Exception) {
    Flux.error<Void>(ex)
        .doOnError { e -> outputHandler!!.error("error before query", e) }
        .onErrorResume { Flux.empty() }
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

    @Throws(Exception::class)
    @JvmStatic
    fun main(vararg args: String) {
      fromArgs(*args).run()
    }
  }
}

// Temp shitty workaround
private fun <T> Flux<T>.takeN(request: Int) =
        if (request < Int.MAX_VALUE) this.limitRate(request).take(request.toLong()) else this
