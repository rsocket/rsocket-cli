package io.rsocket.cli.ws

import io.rsocket.DuplexConnection
import io.rsocket.transport.ClientTransport
import okhttp3.OkHttpClient
import okhttp3.Request
import reactor.core.publisher.Mono
import java.net.URI

class WsClientTransport @JvmOverloads constructor(
  private val uri: String,
  private val transportHeadersFn: () -> Map<String, String> = { mutableMapOf() }
) : ClientTransport {
  override fun connect() = Mono.defer<DuplexConnection> {
    val client = OkHttpClient()

    val urlString = uri.replace("^ws".toRegex(), "http")
    val request = Request.Builder().url(urlString).apply {
      transportHeadersFn().forEach { (name, value) ->
        addHeader(name, value)
      }
    }.build()

    WsDuplexConnection.connect(client, request)
  }
}
