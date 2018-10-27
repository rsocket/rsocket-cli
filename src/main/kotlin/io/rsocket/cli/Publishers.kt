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

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Files
import java.util.stream.Stream

object Publishers {
  fun splitInLines(inputFile: File): Flux<String> {
    return Flux.using({ Files.lines(inputFile.toPath()) }, { Flux.fromStream(it) }, Stream<String>::close).subscribeOn(Schedulers.elastic())
  }

  fun read(inputFile: File): Mono<String> {
    return Mono.defer { Mono.just(inputFile.readText()) }.subscribeOn(Schedulers.elastic())
  }
}
