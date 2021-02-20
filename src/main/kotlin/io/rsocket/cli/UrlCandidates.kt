package io.rsocket.cli

import io.rsocket.cli.Main.Companion.settingsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.buffer

@OptIn(ExperimentalFileSystem::class)
internal class UrlCandidates constructor(val fileSystem: FileSystem = FileSystem.SYSTEM) : Iterable<String> {
  override fun iterator(): Iterator<String> {
    return runBlocking { knownUrls() }.iterator()
  }

  suspend fun knownUrls(): List<String> {
    return withContext(Dispatchers.IO) {
      if (fileSystem.exists(completionFile)) {
        readCompletions()
      } else {
        listOf("wss://rsocket-demo.herokuapp.com/rsocket")
      }
    }
  }

  suspend fun readCompletions() = withContext(Dispatchers.IO) {
    fileSystem.read(completionFile) { readUtf8() }
  }.lines().filter { it.isNotBlank() }

  suspend fun recordUrl(url: String) {
    withContext(Dispatchers.IO) {
      if (!fileSystem.exists(completionFile)) {
        fileSystem.createDirectories(completionFile.parent!!)
        fileSystem.write(completionFile) { writeUtf8("$url\n") }
      } else {
        val known = fileSystem.read(completionFile) { readUtf8() }.lines()

        if (!known.contains(url)) {
          fileSystem.appendingSink(completionFile).use {
            it.buffer().writeUtf8("$url\n")
          }
        }
        ""
      }
    }
  }

  companion object {
    val completionFile = settingsDir / "completions.txt"
  }
}
