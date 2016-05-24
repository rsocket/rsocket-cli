# ReactiveSocket CLI

## Description

Simple ReactiveSocket CLI currently for two main purposes

1. Early testing of new protocol server implementations e.g. websocket
2. Sending some basic traffic to servers built using ReactiveSocket e.g. help debug a mobile <=> server integration issue. 

## Running

```
$ gradle installDist
$ ./build/install/reactivesocket-cli/bin/reactivesocket-cli tcp://localhost:8765
```

## Installing via Homebrew

```
$ brew install yschimke/tap/reactivesocket-cli
$ reactivesocket-cli tcp://localhost:8765
```

## Caveats

- currently really barebones, needs to be shaped by usage in anger
- This may be rewritten in C++ at some point soon when transports are ready in C++.
- This isn't a general purpose tool like curl for making web requests, but rather a manual test tool crutch.
- It will probably never have all the features you want for testing application functionality of ReactiveSocket applications. e.g. your Payloads custom binary format.

