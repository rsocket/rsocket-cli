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

import java.net.URI
import java.util.Objects

/** Utilities for dealing with with [URI]s  */
object UriUtils {
  /**
   * Returns the port of a URI. If the port is unset (i.e. `-1`) then returns the `defaultPort`.
   *
   * @param uri the URI to extract the port from
   * @param defaultPort the default to use if the port is unset
   * @return the port of a URI or `defaultPort` if unset
   * @throws NullPointerException if `uri` is `null`
   */
  fun getPort(uri: URI, defaultPort: Int): Int {
    Objects.requireNonNull(uri, "uri must not be null")
    return if (uri.port == -1) defaultPort else uri.port
  }

  /**
   * Returns whether the URI has a secure schema. Secure is defined as being either `wss` or
   * `https`.
   *
   * @param uri the URI to examine
   * @return whether the URI has a secure schema
   * @throws NullPointerException if `uri` is `null`
   */
  fun isSecure(uri: URI): Boolean {
    Objects.requireNonNull(uri, "uri must not be null")
    return uri.scheme == "wss" || uri.scheme == "https"
  }
}
