package io.rsocket.cli

import com.baulsupp.oksocial.output.UsageException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.LinkedHashMap
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.regex.Pattern

fun expectedFile(name: String): File {
  val file = File(normalize(name))

  if (!file.isFile) {
    throw UsageException("file not found: $file")
  }

  return file
}

private fun normalize(path: String): String = when {
  path.startsWith("~/") -> System.getenv("HOME") + "/" + path.substring(2)
  else -> path
}

fun headerMap(headers: List<String>?): Map<String, String> {
  val headerMap = LinkedHashMap<String, String>()

  if (headers != null) {
    for (header in headers) {
      if (header.startsWith("@")) {
        headerMap.putAll(headerFileMap(header))
      } else {
        val parts = header.split(":".toRegex(), 2).toTypedArray()
        // TODO: consider better strategy than simple trim
        val name = parts[0].trim { it <= ' ' }
        val value = stringValue(parts[1].trim { it <= ' ' })
        headerMap[name] = value
      }
    }
  }
  return headerMap
}

private fun headerFileMap(input: String): Map<out String, String> =
  headerMap(Publishers.splitInLines(inputFile(input)).collectList().block())

fun stringValue(source: String): String = when {
  source.startsWith("@") -> try {
    Publishers.read(inputFile(source)).block()!!
  } catch (e: IOException) {
    throw UsageException(e.toString())
  }
  else -> source
}

fun inputFile(path: String): File = expectedFile(path.substring(1))

private val activeLoggers = mutableListOf<java.util.logging.Logger>()

fun configureLogging(debug: Boolean) {
  InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)

  LogManager.getLogManager().reset()

  val activeLogger = getLogger("")
  val handler = ConsoleHandler()
  handler.level = Level.ALL
  handler.formatter = OneLineLogFormat
  activeLogger.addHandler(handler)

  if (debug) {
    getLogger("").level = Level.INFO
    getLogger("io.netty").level = Level.INFO
    getLogger("io.reactivex").level = Level.FINE
    getLogger("io.rsocket").level = Level.FINEST
    getLogger("reactor.ipc.netty").level = Level.FINEST
  } else {
    getLogger("").level = Level.SEVERE
    getLogger("io.netty").level = Level.SEVERE
    getLogger("io.reactivex").level = Level.SEVERE
    getLogger("io.rsocket").level = Level.SEVERE
  }
}

private fun getLogger(name: String): java.util.logging.Logger {
  val logger = java.util.logging.Logger.getLogger(name)
  activeLoggers.add(logger)
  return logger
}

fun encodeMetadataMap(headerMap: Map<String, String>, mimeType: String): ByteArray {
  return when (mimeType) {
    "application/json" -> jsonEncodeStringMap(headerMap)
    "application/cbor" -> cborEncodeStringMap(headerMap)
    else -> throw UsageException("headers not supported with mimetype '$mimeType'")
  }
}

private fun jsonEncodeStringMap(headerMap: Map<String, String>): ByteArray = try {
  ObjectMapper().writeValueAsBytes(headerMap)
} catch (e: JsonProcessingException) {
  throw RuntimeException(e)
}

private fun cborEncodeStringMap(headerMap: Map<String, String>): ByteArray = try {
  ObjectMapper(CBORFactory()).writeValueAsBytes(headerMap)
} catch (e: JsonProcessingException) {
  throw RuntimeException(e)
}

object OneLineLogFormat : Formatter() {
  private val d = DateTimeFormatterBuilder()
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .optionalStart()
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 3, 3, true)
    .toFormatter()

  private val offset = ZoneOffset.systemDefault()

  override fun format(record: LogRecord): String {
    val message = OneLineLogFormat.formatMessage(record)

    val time = Instant.ofEpochMilli(record.millis).atZone(offset)

    return if (record.thrown != null) {
      val sw = StringWriter(4096)
      val pw = PrintWriter(sw)
      record.thrown.printStackTrace(pw)
      String.format("%s\t%s%n%s%n", time.format(d), message, sw.toString())
    } else {
      String.format("%s\t%s%n", time.format(d), message)
    }
  }
}

private val DURATION_FORMAT = Pattern.compile("(\\d+)(ms|s|m)")

fun parseShortDuration(keepalive: String): Duration {
  val match = DURATION_FORMAT.matcher(keepalive)

  if (!match.matches()) {
    throw UsageException("Unknown duration format '$keepalive'")
  }

  val amount = java.lang.Long.valueOf(match.group(1))!!
  val unit = match.group(2)

  return when (unit) {
    "ms" -> Duration.ofMillis(amount)
    "s" -> Duration.ofSeconds(amount)
    else -> Duration.ofMinutes(amount)
  }
}

fun <T> Flux<T>.takeN(request: Int): Flux<T> =
  if (request < Int.MAX_VALUE) this.limitRate(request).take(request.toLong()) else this

fun <T> Flux<T>.onNext(block: suspend (T) -> Unit): Flux<T> {
  return this.concatMap {
    GlobalScope.mono(Dispatchers.Default) {
      block.invoke(it)
    }.thenMany(Flux.just(it))
  }
}

fun <T> Mono<T>.onNext(block: suspend (T) -> Unit): Mono<T> {
  return this.flatMap {
    GlobalScope.mono(Dispatchers.Default) {
      block.invoke(it)
    }.then(Mono.just(it))
  }
}

fun <T> Flux<T>.onError(block: suspend (Throwable) -> Unit): Flux<T> {
  return this.onErrorResume {
    GlobalScope.mono(Dispatchers.Default) {
      block.invoke(it)
    }.thenMany(Flux.error(it))
  }
}

fun <T> Mono<T>.onError(block: suspend (Throwable) -> Unit): Mono<T> {
  return this.onErrorResume {
    GlobalScope.mono(Dispatchers.Default) {
      block.invoke(it)
    }.then(Mono.error(it))
  }
}
