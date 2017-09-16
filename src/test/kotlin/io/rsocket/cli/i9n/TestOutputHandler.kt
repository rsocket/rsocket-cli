package io.rsocket.cli.i9n

import com.google.common.collect.Lists
import io.rsocket.cli.OutputHandler
import io.rsocket.cli.UsageException

class TestOutputHandler : OutputHandler {
  val stdout: MutableList<String> = Lists.newArrayList()
  val stderr: MutableList<String> = Lists.newArrayList()

  override fun showOutput(output: String) {
    stdout.add(output)
  }

  override fun info(msg: String) {
    stderr.add(msg)
  }

  override fun error(msg: String, e: Throwable) {
    if (e is UsageException) {
      stderr.add(e.message.toString())
    } else {
      stderr.add(msg + ": " + e.toString())
    }
  }

  override fun hashCode(): Int {
    return stdout.hashCode() + stderr.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (other is TestOutputHandler) {
      return stderr == other.stderr && stdout == other.stdout
    }

    return false
  }

  override fun toString(): String {
    val sb = StringBuilder(4096)

    if (!stdout.isEmpty()) {
      sb.append("STDOUT:\n")
      stdout.forEach { s -> sb.append(s).append("\n") }
    }

    if (!stderr.isEmpty()) {
      sb.append("STDERR:\n")
      stderr.forEach { s -> sb.append(s).append("\n") }
    }

    return sb.toString()
  }
}
