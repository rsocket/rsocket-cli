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
import com.baulsupp.oksocial.output.SimpleResponse
import com.baulsupp.oksocial.output.SimpleResponseExtractor
import com.baulsupp.oksocial.output.UsageException
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readByteBuffer
import io.ktor.utils.io.core.readBytes
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.logging.DefaultLoggerFactory
import io.rsocket.kotlin.logging.NoopLogger
import io.rsocket.kotlin.metadata.CompositeMetadata
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.toPacket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import okio.ByteString.Companion.toByteString
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.system.exitProcess
import kotlin.text.isEmpty
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toByteArray
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

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

  @Option(names = ["-s", "--setup"], description = ["String input or @path/to/file for setup metadata"])
  var setup: String? = null

  @Option(names = ["--route"], description = ["RSocket Route"])
  var route: String? = null

  @Option(names = ["--debug"], description = ["Debug Output"])
  var debug: Boolean = false

  @Option(names = ["--timeout"], description = ["Timeout in seconds"])
  var timeout: Long? = null

  @Option(names = ["--keepalive"], description = ["Keepalive period"])
  var keepalive: Int? = null

  @Option(names = ["--requestn", "-r"], description = ["Request N credits"])
  var requestN = Integer.MAX_VALUE

  @Option(names = ["--resume"], description = ["resume enabled"])
  var resume: Boolean = false

  @Option(names = ["--complete"], description = ["Complete Argument"])
  var complete: String? = null

  @Parameters(arity = "0..1", paramLabel = "target", description = ["Endpoint URL"],
    completionCandidates = UrlCandidates::class)
  var target: String? = null

  lateinit var client: RSocket

  val outputHandler by lazy {
    ConsoleHandler.instance(SimpleResponseExtractor)
  }

  override fun run() {
    runBlocking {
      exec()
    }
  }

  private suspend fun exec() {
    configureLogging(debug)

    if (listOf(metadataPush, stream, fireAndForget, channel, requestResponse).all { !it }) {
      stream = true
    }

    if (complete != null) {
      printCompletions()
      return
    }

    val uri = sanitizeUri(target ?: throw UsageException("no target specified"))

    if (metadataFormat == null) {
      if (route != null) {
        metadataFormat = "message/x.rsocket.composite-metadata.v0"
      } else {
        metadataFormat = "application/json"
      }
    }

    if (dataFormat == null) {
      dataFormat = "application/json"
    }

    if (!this::client.isInitialized) {
      val setupPayload = parseSetupPayload()
      client = buildRSocket(uri, setupPayload)
      UrlCandidates.recordUrl(uri)
    }

    runQuery()
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun buildRSocket(
    uri: String,
    setupPayload: Payload
  ) = buildClient(uri) {
    loggerFactory = if (debug) DefaultLoggerFactory else NoopLogger
    connectionConfig {
      setupPayload(setupPayload)
      keepAlive = KeepAlive((keepalive ?: 5).seconds)
      payloadMimeType = PayloadMimeType(dataFormat!!, metadataFormat!!)
    }
  }

  private fun printCompletions() {
    val completions = when (complete) {
      "url" -> UrlCandidates().toList()
      else -> listOf()
    }

    println(completions.joinToString("\n"))
  }

  private suspend fun runQuery() {
    if (timeout != null) {
      withTimeout(TimeUnit.SECONDS.toMillis(timeout!!)) {
        run(client)
      }
    } else {
      run(client)
    }
  }

  private suspend fun parseSetupPayload(): Payload = withContext(Dispatchers.IO) {
    val setup = setup
    when {
      setup == null -> Payload.Empty
      setup.startsWith("@") -> buildPayload {
        data(expectedFile(setup.substring(1)).readBytes())
      }
      else -> buildPayload {
        data(setup)
      }
    }
  }

  private fun sanitizeUri(uri: String): String {
    val validationUri = URI(uri)
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

  @OptIn(ExperimentalMetadataApi::class)
  suspend fun buildMetadata(): ByteArray? = when {
    this.route != null -> {
      CompositeMetadata(RoutingMetadata(route!!)).toPacket().readBytes()
    }
    this.metadata != null -> {
      if (this.headers != null) {
        throw UsageException("Can't specify headers and metadata")
      }

      getInputFromSource(this.metadata, Supplier { ""; }).toByteArray(StandardCharsets.UTF_8)
    }
    this.headers != null -> jsonEncodeStringMap(headerMap(headers))
    else -> null
  }

  private suspend fun singleInputPayload(): Payload {
    val inputBytes = input?.toByteArray() ?: byteArrayOf()
    val metadata = buildMetadata()
    return Payload(ByteReadPacket(inputBytes), metadata?.let { ByteReadPacket(it) })
  }

  suspend fun run(client: RSocket) {
    val inputPayload = singleInputPayload()

    when {
      fireAndForget -> client.fireAndForget(inputPayload)
      metadataPush -> client.metadataPush(inputPayload.data)
      requestResponse -> client.requestResponse(inputPayload)
        .also { showResponse(it) }
      stream -> client.requestStream(inputPayload).take(requestN)
        .collect { showResponse(it) }
      channel -> client.requestChannel(inputPayload, emptyFlow()).take(requestN)
        .collect { showResponse(it) }
      else -> error("No operation to run")
    }
  }

  private suspend fun showResponse(it: Payload) {
    outputHandler.showOutput(SimpleResponse(dataFormat, it.data.readByteBuffer().toByteString()))
  }

  companion object {
    const val NAME = "rsocket-cli"

    var homeDir = System.getProperty("user.home")
    var settingsDir = File(homeDir, ".rsocket-cli")

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
        exitProcess(cmd.commandSpec.exitCodeOnInvalidInput())
      } catch (ex: Exception) {
        ex.printStackTrace(cmd.err)
        exitProcess(cmd.commandSpec.exitCodeOnExecutionException())
      }
    }
  }
}
