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

import io.rsocket.Closeable
import io.rsocket.DuplexConnection
import io.rsocket.cli.http2.Http2UriHandler
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Registry for looking up transports by URI.
 */
object UriTransportRegistry {
  private val FAILED_CLIENT_LOOKUP =
    ClientTransport { Mono.error<DuplexConnection>(UnsupportedOperationException()) }

  private val FAILED_SERVER_LOOKUP =
    ServerTransport { _, _ -> Mono.error<Closeable>(UnsupportedOperationException()) }

  fun clientForUri(uriString: String): ClientTransport {
    val uri = URI.create(uriString)

    for (h in handlers) {
      val r = h.buildClient(uri)
      if (r.isPresent) {
        return r.get()
      }
    }

    return FAILED_CLIENT_LOOKUP
  }

  fun serverForUri(uriString: String): ServerTransport<*> {
    val uri = URI.create(uriString)

    for (h in handlers) {
      val r = h.buildServer(uri)
      if (r.isPresent) {
        return r.get()
      }
    }

    return FAILED_SERVER_LOOKUP
  }

  private val handlers: List<UriHandler> =
        listOf(WebsocketUriHandler(), TcpUriHandler(), Http2UriHandler(), LocalUriHandler())
}
