package io.rsocket

import io.rsocket.cli.Main

fun main() {
  Main.main("--route", "searchTweets", "-i", "michigan", "wss://rsocket-demo.herokuapp.com/rsocket")
}
