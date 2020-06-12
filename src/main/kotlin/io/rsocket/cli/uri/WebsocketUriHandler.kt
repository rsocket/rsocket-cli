/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.cli.uri

import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import io.rsocket.transport.netty.server.WebsocketServerTransport
import java.net.URI
import java.util.Arrays
import java.util.Objects
import java.util.Optional

/**
 * An implementation of [UriHandler] that creates [WebsocketClientTransport]s and [ ]s.
 */
class WebsocketUriHandler : UriHandler {
  override fun buildClient(
    uri: URI,
    headerMap: Map<String, String>
  ): Optional<ClientTransport> {
    Objects.requireNonNull(uri, "uri must not be null")
    return if (SCHEME.stream().noneMatch { scheme: String -> scheme == uri.scheme }) {
      Optional.empty()
    } else {
      Optional.of(WebsocketClientTransport.create(uri).apply {
        setTransportHeaders { headerMap }
      })
    }
  }

  override fun buildServer(uri: URI): Optional<ServerTransport<*>> {
    Objects.requireNonNull(uri, "uri must not be null")
    if (SCHEME.stream()
        .noneMatch { scheme: String -> scheme == uri.scheme }
    ) {
      return Optional.empty()
    }
    val port = if (UriUtils.isSecure(
        uri
      )
    ) UriUtils.getPort(
      uri, 443
    ) else UriUtils.getPort(uri, 80)
    return Optional.of(WebsocketServerTransport.create(uri.host, port))
  }

  companion object {
    private val SCHEME =
      Arrays.asList("ws", "wss", "http", "https")
  }
}
