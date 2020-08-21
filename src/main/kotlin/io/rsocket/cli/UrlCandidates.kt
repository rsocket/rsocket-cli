package io.rsocket.cli

import io.rsocket.cli.Main.Companion.settingsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

internal class UrlCandidates : Iterable<String> {
  override fun iterator(): Iterator<String> {
    return runBlocking { knownUrls() }.iterator()
  }

  companion object {
    val completionFile = File(settingsDir, "completions.txt")

    suspend fun knownUrls(): List<String> {
      return if (completionFile.exists()) {
        withContext(Dispatchers.IO) { readCompletions() }
      } else {
        listOf("wss://rsocket-demo.herokuapp.com/rsocket")
      }
    }

    suspend fun readCompletions() = withContext(Dispatchers.IO) {
      completionFile.readLines().filter { it.isNotBlank() }
    }

    suspend fun recordUrl(url: String) {
      if (!completionFile.exists()) {
        completionFile.parentFile.mkdirs()
        completionFile.appendText("$url\n")
      } else {
        val known = readCompletions()

        if (!known.contains(url)) {
          completionFile.appendText("$url\n")
        }
      }
    }
  }
}
