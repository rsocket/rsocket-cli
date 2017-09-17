/**
 * Copyright 2015 Netflix, Inc.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.cli

import org.slf4j.LoggerFactory
import java.net.ConnectException

/**
 * Normal output handler. Valid content responses sent to STDOUT for piping into other programs.
 * Informational messages to STDERR for user e.g. without extraneous logging prefixes.
 */
class ConsoleOutputHandler : OutputHandler {

  override fun showOutput(output: String) {
    println(output)
  }

  override fun info(msg: String) {
    System.err.println(msg)
  }

  override fun error(msg: String, e: Throwable) {
    val ex = unwrap(e)
    when (ex) {
      is ConnectException -> {
        logger.debug(msg, ex)
        System.err.println(ex.message)
      }
      is UsageException -> {
        logger.debug(msg, ex)
        System.err.println(ex.message)
      }
      else -> {
        System.err.println(msg)
        ex.printStackTrace()
      }
    }
  }

  private fun unwrap(e: Throwable): Throwable {
    // check for propogation of non RuntimeExceptions
    if (e.javaClass == RuntimeException::class.java) {
      if (e.cause != null && e.cause.toString() == e.message) {
        return e.cause!!
      }
    }

    return e
  }

  companion object {
    private val logger = LoggerFactory.getLogger(ConsoleOutputHandler::class.java)
  }
}
