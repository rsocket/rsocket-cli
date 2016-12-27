/*
  Copyright 2015 Netflix, Inc.
  <p>
  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
  in compliance with the License. You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software distributed under the License
  is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
  or implied. See the License for the specific language governing permissions and limitations under
  the License.
 */
package io.reactivesocket.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;

/**
 * Normal output handler. Valid content responses sent to STDOUT for piping into other programs.
 * Informational messages to STDERR for user e.g. without extraneous logging prefixes.
 */
public class ConsoleOutputHandler implements OutputHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleOutputHandler.class);

    @Override
    public void showOutput(String s) {
        System.out.println(s);
    }

    @Override
    public void info(String s) {
        System.err.println(s);
    }

    @Override
    public void error(String msg, Throwable e) {
        e = unwrap(e);
        if (e instanceof ConnectException) {
            logger.debug(msg, e);
            System.err.println(e.getMessage());
        } else if (e instanceof UsageException) {
            logger.debug(msg, e);
            System.err.println(e.getMessage());
        } else {
            System.err.println(msg);
            e.printStackTrace();
        }
    }

    private Throwable unwrap(Throwable e) {
        // check for propogation of non RuntimeExceptions
        if (e.getClass() == RuntimeException.class) {
            if (e.getCause() != null && e.getCause().toString().equals(e.getMessage())) {
                return e.getCause();
            }
        }

        return e;
    }
}
