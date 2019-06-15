package io.rsocket.cli

internal class UrlCandidates : Iterable<String> {
  override fun iterator(): Iterator<String> {
    return listOf("tcp://localhost:9898", "ws://localhost:9898",
      "wss://rsocket-demo.herokuapp.com/ws").iterator()
  }
}
