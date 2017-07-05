package io.rsocket.cli;

import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

public class SystemInCharSource extends CharSource {
  public static final CharSource INSTANCE = new SystemInCharSource();

  private SystemInCharSource() {}

  @Override
  public Reader openStream() throws IOException {
    return new InputStreamReader(System.in);
  }
}
