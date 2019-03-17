package io.rsocket

import io.rsocket.cli.Main

fun main() {
  Main.main("--debug", "-i", "Trump", "--stream", "wss://rsocket-demo.herokuapp.com/ws")
}
