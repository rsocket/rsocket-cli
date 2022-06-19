package io.rsocket.cli

import com.baulsupp.schoutput.UsageException
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.transport.ktor.tcp.TcpClientTransport
import io.rsocket.kotlin.transport.ktor.websocket.client.WebSocketClientTransport

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
  val (hostname, port) = "tcp://([^:]+):(\\d+)".toRegex().matchEntire(uri)?.destructured
    ?: throw UsageException("bad uri format: '$uri'")

  val transport = TcpClientTransport(hostname, port.toInt())
  return RSocketConnector(builder).connect(transport)
}

suspend fun buildWsClient(
  uri: String,
  builder: RSocketConnectorBuilder.() -> Unit
): RSocket {
  val engine: HttpClientEngineFactory<*> = OkHttp

  val transport = WebSocketClientTransport(engine, urlString = uri, secure = uri.startsWith("wss"))
  return RSocketConnector(builder).connect(transport)
}
