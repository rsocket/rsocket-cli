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
import java.net.URI
import java.util.Optional
import java.util.ServiceLoader

/** Maps a [URI] to a [ClientTransport] or [ServerTransport].  */
interface UriHandler {
  /**
   * Returns an implementation of [ClientTransport] unambiguously mapped to a [URI],
   * otherwise [Optional.EMPTY].
   *
   * @param uri the uri to map
   * @return an implementation of [ClientTransport] unambiguously mapped to a [URI], *
   * otherwise [Optional.EMPTY]
   * @throws NullPointerException if `uri` is `null`
   */
  fun buildClient(
    uri: URI,
    headerMap: Map<String, String>
  ): Optional<ClientTransport>

  /**
   * Returns an implementation of [ServerTransport] unambiguously mapped to a [URI],
   * otherwise [Optional.EMPTY].
   *
   * @param uri the uri to map
   * @return an implementation of [ServerTransport] unambiguously mapped to a [URI], *
   * otherwise [Optional.EMPTY]
   * @throws NullPointerException if `uri` is `null`
   */
  fun buildServer(uri: URI): Optional<ServerTransport<*>>

  companion object {
    /**
     * Load all registered instances of `UriHandler`.
     *
     * @return all registered instances of `UriHandler`
     */
    fun loadServices(): ServiceLoader<UriHandler>? {
      return ServiceLoader.load(UriHandler::class.java)
    }
  }
}
