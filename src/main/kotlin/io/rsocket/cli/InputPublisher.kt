package io.rsocket.cli

import io.rsocket.Payload
import reactor.core.publisher.Flux

interface InputPublisher {
  fun singleInputPayload(input: List<String>, metadata: ByteArray?): Payload
  fun inputPublisher(input: List<String>, metadata: ByteArray?): Flux<Payload>
}
