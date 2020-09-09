package io.rsocket

import io.rsocket.cli.Main

fun main() {
  Main.main("--keepalive", "5", "--route", "searchTweets", "-i", "Trump", "wss://rsocket-demo.herokuapp.com/rsocket")
}
