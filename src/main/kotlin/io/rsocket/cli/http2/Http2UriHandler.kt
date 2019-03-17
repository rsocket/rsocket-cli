package io.rsocket.cli.http2

import io.rsocket.Closeable
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import io.rsocket.uri.UriHandler
import org.eclipse.jetty.http.HttpScheme
import java.net.URI
import java.util.Optional
import java.util.Optional.empty
import java.util.Optional.of

class Http2UriHandler : UriHandler {
  override fun buildClient(uri: URI): Optional<ClientTransport> =
    when {
      HttpScheme.HTTPS.`is`(uri.scheme) || HttpScheme.HTTP.`is`(uri.scheme) -> of(Http2ClientTransport(uri))
      else -> empty()
    }

  override fun buildServer(uri: URI): Optional<ServerTransport<Closeable>> = empty()
}
