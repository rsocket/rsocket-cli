package io.rsocket.cli;

import java.util.Map;

public interface HeaderAware {
  void setHeaders(Map<String, String> headers);
}
