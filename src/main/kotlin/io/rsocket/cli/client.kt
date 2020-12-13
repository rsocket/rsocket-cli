package io.rsocket.cli

import com.baulsupp.oksocial.output.UsageException
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.websocket.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.logging.DefaultLoggerFactory
import io.rsocket.kotlin.logging.NoopLogger
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.rSocket
import io.rsocket.kotlin.transport.ktor.clientTransport
import kotlinx.coroutines.Dispatchers
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

suspend fun buildClient(uri: String, dataFormat: String, metadataFormat: String, setupPayload: Payload, debug: Boolean = false): RSocket {
  return if (uri.startsWith("tcp:")) {
    buildTcpClient(uri, dataFormat, metadataFormat, setupPayload, debug)
  } else {
    buildWsClient(uri, dataFormat, metadataFormat, setupPayload, debug)
  }
}

@OptIn(ExperimentalTime::class)
@KtorExperimentalAPI
suspend fun buildTcpClient(uri: String, dataFormat: String, metadataFormat: String, setupPayload: Payload, debug: Boolean = false): RSocket {
  val socket = aSocket(ActorSelectorManager(Dispatchers.IO))

  val (hostname, port) = "tcp://([^:]+):(\\d+)".toRegex().matchEntire(uri)?.destructured
    ?: throw UsageException("bad uri format: '$uri'")

  val transport = socket.tcp().clientTransport(hostname, port.toInt())
  return RSocketConnector() {
    loggerFactory = if (debug) DefaultLoggerFactory else NoopLogger

    connectionConfig {
      setupPayload(setupPayload)
      keepAlive = KeepAlive(5.seconds)
      payloadMimeType = PayloadMimeType(dataFormat, metadataFormat)
    }
  }.connect(transport)
}

@OptIn(ExperimentalTime::class)
@KtorExperimentalAPI
suspend fun buildWsClient(uri: String, dataFormat: String, metadataFormat: String, setupPayload: Payload, debug: Boolean = false): RSocket {
  val engine: HttpClientEngineFactory<*> = OkHttp

  val client = HttpClient(engine) {
    install(WebSockets)
    install(RSocketSupport) {
      connector = RSocketConnector {
        loggerFactory = if (debug) DefaultLoggerFactory else NoopLogger
        connectionConfig {
          setupPayload(setupPayload)

          keepAlive = KeepAlive(5.seconds)
          payloadMimeType = PayloadMimeType(dataFormat, metadataFormat)
        }
      }
    }
  }

  return client.rSocket(uri, uri.startsWith("wss"))
}
