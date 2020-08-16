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
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.WebSockets
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketClientSupport
import io.rsocket.kotlin.core.rSocket
import io.rsocket.kotlin.flow.onRequest
import io.rsocket.kotlin.payload.Payload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.lang.Runnable
import java.net.URI
import java.nio.charset.StandardCharsets
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
    val engine: HttpClientEngineFactory<*> = CIO

    val client = HttpClient(engine) {
      install(WebSockets)
      install(RSocketClientSupport)
    }

    return client.rSocket(uri, uri.startsWith("wss"))
  }

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
      setup == null -> Payload.Empty
      setup!!.startsWith("@") ->
        Payload(expectedFile(setup!!.substring(1)).readBytes())
      else -> Payload(setup!!)
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
      TODO("implement spring routes")
//      val compositeByteBuf = CompositeByteBuf(ByteBufAllocator.DEFAULT, false, 1);
//      val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf(route))
//      CompositeMetadataCodec.encodeAndAddMetadata(compositeByteBuf, ByteBufAllocator.DEFAULT,
//        WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, routingMetadata.content)
//      ByteBufUtil.getBytes(compositeByteBuf)
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
    return Payload(inputBytes, buildMetadata())
  }

  @OptIn(InternalCoroutinesApi::class)
  suspend fun run(client: RSocket) {
    val inputPayload = singleInputPayload()

    when {
      fireAndForget -> client.fireAndForget(inputPayload)
      metadataPush -> client.metadataPush(inputPayload.data)
      requestResponse -> client.requestResponse(inputPayload)
        .also { outputHandler.showOutput(it.data.readText()) }
      stream -> client.requestStream(inputPayload).take(requestN)
        .collect { outputHandler.showOutput(it.data.readText()) }
      channel -> client.requestChannel(flowOf(inputPayload).onRequest {
        TODO("send next n requests")
      }).take(requestN)
        .collect { outputHandler.showOutput(it.data.readText()) }
      else -> error("No operation to run")
    }
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
