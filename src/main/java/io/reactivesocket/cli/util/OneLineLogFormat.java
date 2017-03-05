/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.reactivesocket.cli.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

/**
 * Is Java8 Data and Time really this bad, or is writing this on a plane from just javadocs a bad
 * idea?
 *
 * Why so much construction?
 */
public class OneLineLogFormat extends Formatter {
  private DateTimeFormatter d = new DateTimeFormatterBuilder()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .optionalStart()
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .optionalStart()
      .appendFraction(NANO_OF_SECOND, 3, 3, true)
      .toFormatter();

  private ZoneId offset = ZoneOffset.systemDefault();

  @Override public String format(LogRecord record) {
    String message = formatMessage(record);

    ZonedDateTime time = Instant.ofEpochMilli(record.getMillis()).atZone(offset);

    if (record.getThrown() != null) {
      StringWriter sw = new StringWriter(4096);
      PrintWriter pw = new PrintWriter(sw);
      record.getThrown().printStackTrace(pw);
      return String.format("%s\t%s%n%s%n", time.format(d), message, sw.toString());
    } else {
      return String.format("%s\t%s%n", time.format(d), message);
    }
  }
}
