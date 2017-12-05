package io.rsocket.cli.i9n

import io.rsocket.cli.Main

object PingClient {
  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {
    Main.main("--str", "--debug", "-i", "Hello", "tcp://localhost:7878")
  }
}
