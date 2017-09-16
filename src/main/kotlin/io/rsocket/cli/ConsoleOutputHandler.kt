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

  override fun showOutput(s: String) {
    println(s)
  }

  override fun info(s: String) {
    System.err.println(s)
  }

  override fun error(msg: String, e: Throwable) {
    var e = e
    e = unwrap(e)
    if (e is ConnectException) {
      logger.debug(msg, e)
      System.err.println(e.message)
    } else if (e is UsageException) {
      logger.debug(msg, e)
      System.err.println(e.message)
    } else {
      System.err.println(msg)
      e.printStackTrace()
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
    private val logger = LoggerFactory.getLogger(ConsoleOutputHandler::class.java!!)
  }
}
