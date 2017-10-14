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
package io.rsocket.cli.util

import com.google.common.collect.Lists
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

object LoggingUtil {
    private val activeLoggers = Lists.newArrayList<java.util.logging.Logger>()

    fun configureLogging(debug: Boolean) {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)

        LogManager.getLogManager().reset()

        val activeLogger = getLogger("")
        val handler = ConsoleHandler()
        handler.level = Level.ALL
        handler.formatter = OneLineLogFormat()
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
}
