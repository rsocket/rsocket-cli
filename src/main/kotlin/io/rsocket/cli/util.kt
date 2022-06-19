package io.rsocket.cli

import com.baulsupp.schoutput.UsageException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.regex.Pattern

fun expectedFile(name: String): Path {
  val file = normalize(name).toPath()

  if (!FileSystem.SYSTEM.exists(file)) {
    throw UsageException("file not found: $file")
  }

  return file
}

private fun normalize(path: String): String = when {
  path.startsWith("~/") -> System.getenv("HOME") + "/" + path.substring(2)
  else -> path
}

suspend fun headerMap(headers: List<String>?): Map<String, String> {
  val headerMap = mutableMapOf<String, String>()

  if (headers != null) {
    for (header in headers) {
      if (header.startsWith("@")) {
        headerMap.putAll(headerFileMap(header))
      } else {
        val parts = header.split(":".toRegex(), 2).toTypedArray()
        val name = parts[0].trim { it <= ' ' }
        val value = stringValue(parts[1].trim { it <= ' ' })
        headerMap[name] = value
      }
    }
  }
  return headerMap
}

private suspend fun headerFileMap(input: String): Map<out String, String> {
  return withContext(Dispatchers.IO) {
    headerMap(FileSystem.SYSTEM.read(inputFile(input)) { readUtf8() }.lines())
  }
}

fun stringValue(source: String): String = when {
  source.startsWith("@") -> try {
    FileSystem.SYSTEM.read(inputFile(source)) { readUtf8() }
  } catch (e: IOException) {
    throw UsageException(e.toString())
  }
  else -> source
}

fun inputFile(path: String): Path = expectedFile(path.substring(1))

private val activeLoggers = mutableListOf<java.util.logging.Logger>()

fun configureLogging(debug: Boolean) {
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

val moshi by lazy {
  Moshi.Builder().build()!!
}

fun jsonEncodeStringMap(headerMap: Map<String, String>): ByteArray {
  val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
  return moshi.adapter<Map<String, String>>(type).toJson(headerMap).toByteArray()
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

  val amount = java.lang.Long.valueOf(match.group(1))
  val unit = match.group(2)

  return when (unit) {
    "ms" -> Duration.ofMillis(amount)
    "s" -> Duration.ofSeconds(amount)
    else -> Duration.ofMinutes(amount)
  }
}
