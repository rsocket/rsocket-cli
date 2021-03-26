package io.rsocket

import io.rsocket.cli.Main

fun main() {
  Main.main("--route", "searchTweets", "-i", "london", "wss://demo.rsocket.io/rsocket")
}
