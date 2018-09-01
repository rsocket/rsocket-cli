package io.rsocket.cli

import com.baulsupp.oksocial.output.OutputHandler
import com.baulsupp.oksocial.output.UsageException
import com.google.common.io.Files
import io.rsocket.Payload
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.util.Scanner

class LineInputPublishers(val outputHandler: OutputHandler<*>) : InputPublisher {
  private fun filePublisher(filename: String): Flux<String> {
    return Flux.defer {
      val file = expectedFile(filename)

      if (!file.exists()) {
        Flux.error(UsageException("file not found: $filename"))
      } else {
        val r = Files.newReader(file, StandardCharsets.UTF_8)

        Flux.generate<String> { s ->
          val line = r.readLine()

          if (line != null) {
            s.next(line)
          } else {
            s.complete()
          }
        }.doFinally { r.close() }.subscribeOn(Schedulers.elastic())
      }
    }
  }

  override fun singleInputPayload(input: List<String>, metadata: ByteArray?): Payload {
    return inputPublisher(input, metadata).blockFirst()!!
  }

  override fun inputPublisher(input: List<String>, metadata: ByteArray?): Flux<Payload> {
    return Flux.fromIterable(input).concatMap {
      when {
        it == "-" -> systemInLines()
        it.startsWith("@") -> filePublisher(it.substring(1))
        else -> Flux.just(it)
      }
    }.map {
      DefaultPayload.create(
        it.toByteArray(StandardCharsets.UTF_8),
        metadata
      )
    }
  }

  private fun systemInLines(): Flux<String> {
    val keyboard = Scanner(System.`in`)

    return Flux.generate<String> { s ->
      if (keyboard.hasNext()) {
        s.next(keyboard.nextLine())
      } else {
        s.complete()
      }
    }.doOnSubscribe {
      outputHandler.info("Type commands to send to the server.")
    }.subscribeOn(Schedulers.elastic())
  }
}
