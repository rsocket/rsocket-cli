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
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.logging.DefaultLoggerFactory
import io.rsocket.kotlin.logging.NoopLogger
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.rSocket
import io.rsocket.kotlin.transport.ktor.clientTransport
import kotlinx.coroutines.Dispatchers
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

suspend fun buildClient(
  uri: String,
  builder: RSocketConnectorBuilder.() -> Unit
): RSocket {
  return if (uri.startsWith("tcp:")) {
    buildTcpClient(uri, builder)
  } else {
    buildWsClient(uri, builder)
  }
}

suspend fun buildTcpClient(
  uri: String,
  builder: RSocketConnectorBuilder.() -> Unit
): RSocket {
  val socket = aSocket(ActorSelectorManager(Dispatchers.IO))

  val (hostname, port) = "tcp://([^:]+):(\\d+)".toRegex().matchEntire(uri)?.destructured
    ?: throw UsageException("bad uri format: '$uri'")

  val transport = socket.tcp().clientTransport(hostname, port.toInt())
  return RSocketConnector(builder).connect(transport)
}

suspend fun buildWsClient(
  uri: String,
  builder: RSocketConnectorBuilder.() -> Unit
): RSocket {
  val engine: HttpClientEngineFactory<*> = OkHttp

  val client = HttpClient(engine) {
    install(WebSockets)
    install(RSocketSupport) {
      connector = RSocketConnector(builder)
    }
  }

  return client.rSocket(uri, uri.startsWith("wss"))
}
