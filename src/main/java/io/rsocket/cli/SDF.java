package io.rsocket.cli;

public class SDF {
  public static void main(String[] args) throws Exception {
    Main.main("--debug", "--rr", "tcp://localhost:9898", "-i", "Client");
  }
}
