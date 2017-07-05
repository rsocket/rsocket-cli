package io.rsocket.cli.i9n;

import io.rsocket.cli.Main;

public class PingClient {
  public static void main(String[] args) throws Exception {
    Main.main("--str", "--debug", "-i", "Hello", "tcp://localhost:7878");
  }
}
