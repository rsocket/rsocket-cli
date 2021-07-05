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
import io.rsocket.kotlin.transport.ktor.TcpClientTransport
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.WebSocketClientTransport
import kotlinx.coroutines.Dispatchers

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

@OptIn(InternalAPI::class)
suspend fun buildTcpClient(
  uri: String,
  builder: RSocketConnectorBuilder.() -> Unit
): RSocket {
  val socket = aSocket(ActorSelectorManager(Dispatchers.IO))

  val (hostname, port) = "tcp://([^:]+):(\\d+)".toRegex().matchEntire(uri)?.destructured
    ?: throw UsageException("bad uri format: '$uri'")

  val transport = TcpClientTransport(SelectorManager(), hostname, port.toInt())
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

  val transport = WebSocketClientTransport(client, urlString = uri, secure = uri.startsWith("wss"))
  return RSocketConnector(builder).connect(transport)
}
