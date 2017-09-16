package io.rsocket.cli

interface HeaderAware {
  fun setHeaders(headers: Map<String, String>)
}
