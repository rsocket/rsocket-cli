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
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.TcpServerTransport
import reactor.netty.tcp.TcpServer
import java.net.URI
import java.util.Objects
import java.util.Optional

/**
 * An implementation of [UriHandler] that creates [TcpClientTransport]s and [ ]s.
 */
class TcpUriHandler : UriHandler {
  override fun buildClient(
    uri: URI,
    headerMap: Map<String, String>
  ): Optional<ClientTransport> {
    Objects.requireNonNull(uri, "uri must not be null")
    return if (SCHEME != uri.scheme) {
      Optional.empty()
    } else Optional.of(TcpClientTransport.create(uri.host, uri.port))
  }

  override fun buildServer(uri: URI): Optional<ServerTransport<*>> {
    Objects.requireNonNull(uri, "uri must not be null")
    return if (SCHEME != uri.scheme) {
      Optional.empty()
    } else Optional.of(
      TcpServerTransport.create(
        TcpServer.create()
          .host(uri.host)
          .port(uri.port)
      )
    )
  }

  companion object {
    private const val SCHEME = "tcp"
  }
}
