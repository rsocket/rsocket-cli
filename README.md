# ReactiveSocket CLI

## Description

Simple ReactiveSocket CLI currently for two main purposes

1. Early testing of new protocol server implementations e.g. websocket
2. Sending some basic traffic to servers built using ReactiveSocket e.g. help debug a mobile <=> server integration issue. 

# Build Status

<a href='https://travis-ci.org/ReactiveSocket/reactivesocket-cli/builds'><img src='https://travis-ci.org/ReactiveSocket/reactivesocket-cli.svg?branch=master'></a> 


## Running

```
$ gradle installDist
$ ./build/install/reactivesocket-cli/bin/reactivesocket-cli tcp://localhost:8765
```

## Installing via Homebrew (out of date)

Use tab completion for help with specifying the operation type.

```
$ brew install yschimke/tap/reactivesocket-cli
$ reactivesocket-cli tcp://localhost:8765
```


