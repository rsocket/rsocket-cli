# ReactiveSocket CLI

## Description

Simple ReactiveSocket CLI currently for two main purposes

1. Early testing of new protocol server implementations e.g. websocket
2. Sending some basic traffic to servers built using ReactiveSocket e.g. help debug a mobile <=> server integration issue. 

# Build Status

<a href='https://travis-ci.org/ReactiveSocket/reactivesocket-cli/builds'><img src='https://travis-ci.org/ReactiveSocket/reactivesocket-cli.svg?branch=master'></a> 


## Build and Run (one step)

```
$ ./reactivesocket-cli tcp://localhost:8765
```

## Installing via Homebrew

Use tab completion for help with specifying the operation type.

```
$ brew install yschimke/tap/reactivesocket-cli
$ reactivesocket-cli -i "I am a Server" --server tcp://localhost:8765  # window 1
$ reactivesocket-cli --rr -i "I am a Client" tcp://localhost:8765      # window 2
```

Stream the dictionary (With frames debugged)

```
$ reactivesocket-cli --debug -i @/usr/share/dict/words --server tcp://localhost:8765  # window 1
$ reactivesocket-cli --str -i "Word Up" tcp://localhost:8765                  # window 2
```

