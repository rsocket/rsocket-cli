package io.rsocket.cli.util

import com.google.common.base.Charsets
import com.google.common.io.Files
import io.rsocket.cli.Publishers
import io.rsocket.cli.UsageException
import io.rsocket.cli.util.FileUtil.expectedFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

// TODO handle duplicate header keys
object HeaderUtil {
  fun headerMap(headers: List<String>?): Map<String, String> {
    val headerMap = LinkedHashMap<String, String>()

    if (headers != null) {
      for (header in headers) {
        if (header.startsWith("@")) {
          headerMap.putAll(headerFileMap(header))
        } else {
          val parts = header.split(":".toRegex(), 2).toTypedArray()
          // TODO: consider better strategy than simple trim
          val name = parts[0].trim({ it <= ' ' })
          val value = stringValue(parts[1].trim({ it <= ' ' }))
          headerMap.put(name, value)
        }
      }
    }
    return headerMap
  }

  private fun headerFileMap(input: String): Map<out String, String> =
      headerMap(Publishers.splitInLines(Files.asCharSource(inputFile(input), Charsets.UTF_8)).collectList().block())

  fun stringValue(source: String): String = when {
    source.startsWith("@") -> try {
      Files.toString(inputFile(source), StandardCharsets.UTF_8)
    } catch (e: IOException) {
      throw UsageException(e.toString())
    }
    else -> source
  }

  fun inputFile(path: String): File = expectedFile(path.substring(1))
}
