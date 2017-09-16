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

import com.google.common.base.Charsets
import com.google.common.base.Throwables
import com.google.common.io.CharSource
import com.google.common.io.Files
import io.airlift.airline.*
import io.rsocket.*
import io.rsocket.cli.Main.Companion.NAME
import io.rsocket.cli.Publishers.lines
import io.rsocket.cli.util.FileUtil.expectedFile
import io.rsocket.cli.util.HeaderUtil.headerMap
import io.rsocket.cli.util.HeaderUtil.inputFile
import io.rsocket.cli.util.HeaderUtil.stringValue
import io.rsocket.cli.util.LoggingUtil
import io.rsocket.cli.util.MetadataUtil
import io.rsocket.cli.util.TimeUtil.parseShortDuration
import io.rsocket.uri.UriTransportRegistry
import io.rsocket.util.PayloadImpl
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.function.Supplier

/**
 * Simple command line tool to make a RSocket connection and send/receive elements.
 *
 *
 * Currently limited in features, only supports a text/line based approach.
 */
@Command(name = NAME, description = "CLI for RSocket.")
class Main {

  @Option(name = arrayOf("-h", "--help"), description = "Display help information")
  var help: Boolean = false

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

  @Option(name = arrayOf("-i", "--input"), description = "String input or @path/to/file")
  var input: String? = null

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

  private var server: Closeable? = null

  fun run() {
    LoggingUtil.configureLogging(debug)

    if (outputHandler == null) {
      outputHandler = ConsoleOutputHandler()
    }

    try {
      val uri = arguments[0]

      if (serverMode) {
        val transport = RSocketFactory.receive()
            .acceptor { setupPayload, reactiveSocket -> createServerRequestHandler(setupPayload) }
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

        if (transportHeader != null && clientTransport is HeaderAware) {
          (clientTransport as HeaderAware).setHeaders(headerMap(transportHeader))
        }

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

  private fun standardMimeType(dataFormat: String?): String {
    if (dataFormat == null) {
      return "application/json"
    }

    when (dataFormat) {
      "json" -> return "application/json"
      "cbor" -> return "application/cbor"
      "binary" -> return "application/binary"
      "text" -> return "text/plain"
      else -> return dataFormat
    }
  }

  fun parseSetupPayload(): Payload {
    var source: String? = null

    if (setup!!.startsWith("@")) {
      try {
        source = Files.asCharSource(setupFile(), Charsets.UTF_8).read()
      } catch (e: IOException) {
        throw Throwables.propagate(e)
      }

    } else {
      source = setup
    }

    return PayloadImpl(source!!)
  }

  private fun setupFile(): File {
    return expectedFile(input!!.substring(1))
  }

  fun createServerRequestHandler(setupPayload: ConnectionSetupPayload): Mono<RSocket> {
    LoggerFactory.getLogger(Main::class.java!!).debug("setup payload " + setupPayload)

    return Mono.just(
        object : AbstractRSocket() {
          override fun fireAndForget(payload: Payload?): Mono<Void> {
            showPayload(payload)
            return Mono.empty()
          }

          override fun requestResponse(payload: Payload?): Mono<Payload> {
            return handleIncomingPayload(payload).single()
          }

          override fun requestStream(payload: Payload?): Flux<Payload> {
            return handleIncomingPayload(payload)
          }

          override fun requestChannel(payloads: Publisher<Payload>?): Flux<Payload> {
            Flux.from(payloads!!)
                .subscribe({ p -> showPayload(p) }) { e -> outputHandler!!.error("channel error", e) }
            return inputPublisher()
          }

          override fun metadataPush(payload: Payload?): Mono<Void> {
            outputHandler!!.showOutput(toUtf8String(payload!!.metadata))
            return Mono.empty()
          }
        })
  }

  private fun handleIncomingPayload(payload: Payload?): Flux<Payload> {
    showPayload(payload)
    return inputPublisher()
  }

  private fun showPayload(payload: Payload?) {
    outputHandler!!.showOutput(toUtf8String(payload!!.data))
  }

  private fun toUtf8String(data: ByteBuffer): String {
    return StandardCharsets.UTF_8.decode(data).toString()
  }

  fun run(client: RSocket?): Flux<Void> {
    try {
      return runAllOperations(client)
    } catch (e: Exception) {
      handleError(e)
    }

    return Flux.empty()
  }

  private fun handleError(e: Throwable) {
    outputHandler!!.error("error", e)
  }

  private fun runAllOperations(client: RSocket?): Flux<Void> {
    return Flux.range(0, operations).flatMap { i -> runSingleOperation(client) }
  }

  private fun runSingleOperation(client: RSocket?): Flux<Void> {
    try {
      val source: Flux<Payload>

      if (fireAndForget) {
        source = client!!.fireAndForget(singleInputPayload()).thenMany(Flux.empty())
      } else if (metadataPush) {
        source = client!!.metadataPush(singleInputPayload()).thenMany(Flux.empty())
      } else if (requestResponse) {
        source = client!!.requestResponse(singleInputPayload()).flux()
      } else if (stream) {
        source = client!!.requestStream(singleInputPayload())
      } else if (channel) {
        if (input == null) {
          outputHandler!!.info("Type commands to send to the server.")
        }
        source = client!!.requestChannel(inputPublisher())
      } else {
        outputHandler!!.info("Using passive client mode, choose an option to use a different mode.")
        source = Flux.never()
      }

      return source
          .map({ it.getData() })
          .map({ this.toUtf8String(it) })
          .doOnNext({ outputHandler!!.showOutput(it) })
          .doOnError { e -> outputHandler!!.error("error from server", e) }
          .onErrorResume { e -> Flux.empty() }
          .take(requestN.toLong())
          .thenMany(Flux.empty())
    } catch (ex: Exception) {
      return Flux.error<Void>(ex)
          .doOnError { e -> outputHandler!!.error("error before query", e) }
          .onErrorResume { e -> Flux.empty() }
    }

  }

  private fun inputPublisher(): Flux<Payload> {
    val stream: CharSource

    if (input == null) {
      stream = SystemInCharSource.INSTANCE
    } else if (input!!.startsWith("@")) {
      stream = Files.asCharSource(inputFile(input!!), Charsets.UTF_8)
    } else {
      stream = CharSource.wrap(input!!)
    }

    val metadata = buildMetadata()

    return lines(stream, java.util.function.Function<String, ByteArray> { l -> metadata })
  }

  private fun singleInputPayload(): PayloadImpl {
    val data = getInputFromSource(
        input,
        Supplier<String> {
          Scanner(System.`in`).nextLine()
        })
        .trim { it <= ' ' }

    val metadata = buildMetadata()

    return PayloadImpl(data.toByteArray(StandardCharsets.UTF_8), metadata)
  }

  private fun buildMetadata(): ByteArray {
    if (this.metadata != null) {
      if (this.headers != null) {
        throw UsageException("Can't specify headers and metadata")
      }

      return getInputFromSource(this.metadata, Supplier<String> { ""; }).toByteArray(StandardCharsets.UTF_8)
    } else return if (this.headers != null) {
      MetadataUtil.encodeMetadataMap(headerMap(headers), standardMimeType(metadataFormat))
    } else {
      ByteArray(0)
    }
  }

  companion object {
    const val NAME = "reactivesocket-cli"

    private fun getInputFromSource(source: String?, nullHandler: Supplier<String>): String {
      val s: String

      if (source == null) {
        s = nullHandler.get()
      } else {
        s = stringValue(source)
      }

      return s
    }

    private fun fromArgs(vararg args: String): Main? {
      val cmd = SingleCommand.singleCommand<Main>(Main::class.java!!)
      try {
        return cmd.parse(*args)
      } catch (e: ParseException) {
        System.err.println(e.message)
        Help.help(cmd.commandMetadata)
        System.exit(-1)
        return null
      }

    }

    @Throws(Exception::class)
    @JvmStatic
    fun main(vararg args: String) {
      fromArgs(*args)!!.run()
    }
  }
}
