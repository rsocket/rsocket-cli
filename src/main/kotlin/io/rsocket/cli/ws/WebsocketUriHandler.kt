package io.rsocket.cli.ws

import io.rsocket.Closeable
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import io.rsocket.uri.UriHandler
import org.eclipse.jetty.http.HttpScheme
import java.net.URI
import java.util.Optional
import java.util.Optional.empty
import java.util.Optional.of

class WebsocketUriHandler : UriHandler {
  override fun buildClient(uri: URI): Optional<ClientTransport> =
    when {
      HttpScheme.WSS.`is`(uri.scheme) || HttpScheme.WS.`is`(uri.scheme) -> of(
        WebsocketClientTransport.create(uri))
      else -> empty()
    }

  override fun buildServer(uri: URI): Optional<ServerTransport<Closeable>> = empty()
}
