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
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.CompositeByteBuf
import io.rsocket.Closeable
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.cli.ws.WsClientTransport
import io.rsocket.core.RSocketConnector
import io.rsocket.core.Resume
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.TaggingMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.transport.ClientTransport
import io.rsocket.util.DefaultPayload
import io.rsocket.util.EmptyPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.util.retry.Retry
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration.ofSeconds
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.system.exitProcess

/**
 * Simple command line tool to make a RSocket connection and send/receive elements.
 *
 * Currently limited in features, only supports a text/line based approach.
 */
@Command(description = ["RSocket CLI command"],
  name = "rsocket-cli", mixinStandardHelpOptions = true, version = ["dev"])
class Main : Runnable {
  @Option(names = ["-H", "--header"], description = ["Custom header to pass to server"])
  var headers: List<String>? = null

  @Option(names = ["-T", "--transport-header"], description = ["Custom header to pass to the transport"])
  var transportHeader: List<String>? = null

  @Option(names = ["--stream"], description = ["Request Stream"])
  var stream: Boolean = false

  @Option(names = ["--request"], description = ["Request Response"])
  var requestResponse: Boolean = false

  @Option(names = ["--fnf"], description = ["Fire and Forget"])
  var fireAndForget: Boolean = false

  @Option(names = ["--channel"], description = ["Channel"])
  var channel: Boolean = false

  @Option(names = ["--metadataPush"], description = ["Metadata Push"])
  var metadataPush: Boolean = false

  @Option(names = ["-i", "--input"], description = ["String input or @path/to/file"])
  var input: String? = null

  @Option(names = ["-m", "--metadata"],
    description = ["Metadata input string input or @path/to/file"])
  var metadata: String? = null

  @Option(names = ["--metadataFormat"], description = ["Metadata Format"])
  var metadataFormat: String? = null

  @Option(names = ["--dataFormat"], description = ["Data Format"])
  var dataFormat: String? = null

  @Option(names = ["--setup"], description = ["String input or @path/to/file for setup metadata"])
  var setup: String? = null

  @Option(names = ["--route"], description = ["RSocket Route"])
  var route: String? = null

  @Option(names = ["--debug"], description = ["Debug Output"])
  var debug: Boolean = false

  @Option(names = ["--timeout"], description = ["Timeout in seconds"])
  var timeout: Long? = null

  @Option(names = ["--keepalive"], description = ["Keepalive period"])
  var keepalive: String? = null

  @Option(names = ["--requestn", "-r"], description = ["Request N credits"])
  var requestN = Integer.MAX_VALUE

  @Option(names = ["--resume"], description = ["resume enabled"])
  var resume: Boolean = false

  @Parameters(arity = "0..1", paramLabel = "target", description = ["Endpoint URL"],
    completionCandidates = UrlCandidates::class)
  var target: String? = null

  lateinit var client: RSocket

  lateinit var outputHandler: OutputHandler<Any>

  lateinit var server: Closeable

  override fun run() {
    runBlocking {
      exec()
    }
  }

  private suspend fun exec() {
    configureLogging(debug)

    if (!this::outputHandler.isInitialized) {
      outputHandler = ConsoleHandler.instance()
    }

    if (listOf(metadataPush, stream, fireAndForget, channel, requestResponse).all { !it }) {
      stream = true
    }

    if (route != null) {
      if (metadataFormat == null) {
        metadataFormat = "composite"
      }

      if (dataFormat == null) {
        dataFormat = "json"
      }
    } else {
      if (metadataFormat == null) {
        metadataFormat = "json"
      }

      if (dataFormat == null) {
        dataFormat = "binary"
      }
    }

    val uri = sanitizeUri(target ?: throw UsageException("no target specified"))

    if (!this::client.isInitialized) {
      client = buildClient(uri)
    }

    if (timeout != null) {
      withTimeout(TimeUnit.SECONDS.toMillis(timeout!!)) {
        run(client)
      }
    } else {
      run(client)
    }
  }

  suspend fun buildClient(uri: String): RSocket {
    val clientTransport = clientForUri(uri, headerMap(transportHeader))

    return RSocketConnector.create()
      .apply {
        if (resume) {
          resume(Resume().sessionDuration(ofSeconds(30)).retry(Retry.fixedDelay(3, ofSeconds(3))))
        }

        if (keepalive != null) {
          val duration = parseShortDuration(keepalive!!)
          keepAlive(duration, duration.multipliedBy(3))
        }
      }
      .metadataMimeType(standardMimeType(metadataFormat))
      .dataMimeType(standardMimeType(dataFormat))
      .setupPayload(parseSetupPayload())
      .connect(clientTransport)
      .awaitSingle()
  }

  private fun clientForUri(uri: String, headerMap: Map<String, String>): ClientTransport =
    WsClientTransport(uri) { headerMap }

  private fun standardMimeType(dataFormat: String?): String = when (dataFormat) {
    null -> "application/json"
    "json" -> "application/json"
    "cbor" -> "application/cbor"
    "binary" -> "application/binary"
    "text" -> "text/plain"
    "composite" -> "message/x.rsocket.composite-metadata.v0"
    else -> dataFormat
  }

  private suspend fun parseSetupPayload(): Payload = withContext(Dispatchers.IO) {
    when {
      setup == null -> EmptyPayload.INSTANCE
      setup!!.startsWith("@") ->
        DefaultPayload.create(expectedFile(setup!!.substring(1)).readBytes())
      else -> DefaultPayload.create(setup!!)
    }
  }

  private fun sanitizeUri(uri: String): String {
    var validationUri = URI(uri)
    if (validationUri.scheme == "ws" || validationUri.scheme == "wss") {
      if (validationUri.path.isEmpty()) {
        return "$uri/"
      }
    }
    return uri
  }

  fun getInputFromSource(source: String?, nullHandler: Supplier<String>): String =
    when (source) {
      null -> nullHandler.get()
      else -> stringValue(source)
    }

  suspend fun buildMetadata(): ByteArray? = when {
    this.route != null -> {
      val compositeByteBuf = CompositeByteBuf(ByteBufAllocator.DEFAULT, false, 1);
      val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf(route))
      CompositeMetadataCodec.encodeAndAddMetadata(compositeByteBuf, ByteBufAllocator.DEFAULT,
        WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, routingMetadata.content)
      ByteBufUtil.getBytes(compositeByteBuf)
    }
    this.metadata != null -> {
      if (this.headers != null) {
        throw UsageException("Can't specify headers and metadata")
      }

      getInputFromSource(this.metadata, Supplier { ""; }).toByteArray(StandardCharsets.UTF_8)
    }
    this.headers != null -> encodeMetadataMap(headerMap(headers), standardMimeType(metadataFormat))
    else -> ByteArray(0)
  }

  private suspend fun singleInputPayload(): Payload {
    // TODO readd support for files
    val inputBytes = input?.toByteArray() ?: byteArrayOf()
    return DefaultPayload.create(inputBytes, buildMetadata())
  }

  suspend fun run(client: RSocket) {
    val inputPayload = singleInputPayload()

    val flux = when {
      fireAndForget -> client.fireAndForget(inputPayload).thenMany(empty())
      metadataPush -> client.metadataPush(inputPayload).thenMany(empty())
      requestResponse -> client.requestResponse(inputPayload).flux()
      stream -> client.requestStream(inputPayload)
      channel -> client.requestChannel(Flux.just(inputPayload))
      else -> Flux.never()
    }

    flux
      .asFlow()
      .take(requestN)
      .map { it.dataUtf8 }
      .collect { outputHandler.showOutput(it) }
  }

  companion object {
    const val NAME = "reactivesocket-cli"

    @JvmStatic
    fun main(vararg args: String) {
      val main = Main()
      exec(main, args.toList())
    }

    private fun exec(runnable: Main, args: List<String>) {
      val cmd = CommandLine(runnable)
      try {
        val parseResult = cmd.parseArgs(*args.toTypedArray())

        if (cmd.isUsageHelpRequested) {
          cmd.usage(cmd.out)
          exitProcess(parseResult.commandSpec().exitCodeOnUsageHelp())
        } else if (cmd.isVersionHelpRequested) {
          cmd.printVersionHelp(cmd.out)
          exitProcess(parseResult.commandSpec().exitCodeOnVersionHelp())
        }

        runnable.run()

        exitProcess(0)
      } catch (pe: CommandLine.ParameterException) {
        cmd.err.println(pe.message)
        if (!CommandLine.UnmatchedArgumentException.printSuggestions(pe, cmd.err)) {
          cmd.usage(cmd.err)
        }
        exitProcess(cmd.commandSpec.exitCodeOnInvalidInput())
      } catch (ue: UsageException) {
        cmd.err.println(ue.message)
        cmd.usage(cmd.err)
        exitProcess(cmd.commandSpec.exitCodeOnInvalidInput())
      } catch (ex: Exception) {
        ex.printStackTrace(cmd.err)
        exitProcess(cmd.commandSpec.exitCodeOnExecutionException())
      }
    }
  }
}
