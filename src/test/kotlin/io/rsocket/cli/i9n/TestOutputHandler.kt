package io.rsocket.cli.i9n

import com.baulsupp.oksocial.output.OutputHandler
import com.baulsupp.oksocial.output.UsageException
import com.google.common.collect.Lists

class TestOutputHandler : OutputHandler<Any> {
  private val stdout: MutableList<String> = Lists.newArrayList()
  private val stderr: MutableList<String> = Lists.newArrayList()

  override suspend fun showOutput(output: Any) {
    stdout.add(output.toString())
  }

  override fun info(msg: String) {
    stderr.add(msg)
  }

  override suspend fun showError(msg: String?, e: Throwable?) {
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
