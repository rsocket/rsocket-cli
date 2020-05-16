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
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.local.LocalClientTransport
import io.rsocket.transport.local.LocalServerTransport
import java.net.URI
import java.util.Objects
import java.util.Optional

/**
 * An implementation of [UriHandler] that creates [LocalClientTransport]s and [ ]s.
 */
class LocalUriHandler : UriHandler {
  override fun buildClient(uri: URI): Optional<ClientTransport> {
    Objects.requireNonNull(uri, "uri must not be null")
    return if (SCHEME != uri.scheme) {
      Optional.empty()
    } else Optional.of(LocalClientTransport.create(uri.schemeSpecificPart))
  }

  override fun buildServer(uri: URI): Optional<ServerTransport<*>> {
    Objects.requireNonNull(uri, "uri must not be null")
    return if (SCHEME != uri.scheme) {
      Optional.empty()
    } else Optional.of(LocalServerTransport.create(uri.schemeSpecificPart))
  }

  companion object {
    private const val SCHEME = "local"
  }
}
