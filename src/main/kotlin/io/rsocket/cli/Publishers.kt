/*
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.cli

import com.google.common.io.CharSource
import io.rsocket.Payload
import io.rsocket.util.PayloadImpl
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.function.Function
import reactor.core.publisher.Flux

object Publishers {

  /**
   * Return a publisher for consuming each line from the passed stream.
   *
   * @param inputStream to read.
   */
  fun lines(inputStream: CharSource, metadataFn: Function<String, ByteArray>): Flux<Payload> {
    return splitInLines(inputStream)
        .map { l -> PayloadImpl(l.toByteArray(StandardCharsets.UTF_8), metadataFn.apply(l)) as Payload }
  }

  fun splitInLines(inputStream: CharSource): Flux<String> {
    try {
      return Flux.fromStream(inputStream.openBufferedStream().lines())
    } catch (e: IOException) {
      return Flux.error(e)
    }

  }
}// No instances.
