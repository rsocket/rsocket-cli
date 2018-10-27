package io.rsocket

import io.rsocket.cli.Main

fun main(args: Array<String>) {
  Main.main("--debug", "-i", "Server", "--server", "tcp://localhost:9898")
}
