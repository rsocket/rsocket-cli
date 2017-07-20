package io.rsocket.cli.i9n;

import static org.junit.Assert.assertEquals;

import io.rsocket.AbstractRSocket;
import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.cli.Main;
import io.rsocket.exceptions.ApplicationException;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.rsocket.util.PayloadImpl;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BasicOperationTest {
  private Main main = new Main();
  private TestOutputHandler output = new TestOutputHandler();
  private RSocket client;
  private Closeable server;

  private final TestOutputHandler expected = new TestOutputHandler();

  private RSocket requestHandler = new AbstractRSocket() {};

  private String testName;

  @Rule
  public TestRule watcher =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          testName = description.getMethodName();
        }
      };

  public void connect() {
    main.outputHandler = output;

    server =
        RSocketFactory.receive()
            .acceptor((p, cr) -> Mono.just(requestHandler))
            .transport(LocalServerTransport.create("test-local-server-" + testName))
            .start()
            .block();

    client =
        RSocketFactory.connect()
            .transport(LocalClientTransport.create("test-local-server-" + testName))
            .start()
            .block();
  }

  @After
  public void shutdown() {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.close().block();
    }
  }

  @Test
  public void metadataPush() throws Exception {
    main.metadataPush = true;
    main.input = "Hello";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Mono<Void> metadataPush(Payload payload) {
            return Mono.empty();
          }
        };

    run();

    assertEquals(expected, output);
  }

  @Test
  public void fireAndForget() throws Exception {
    main.fireAndForget = true;
    main.input = "Hello";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Mono<Void> fireAndForget(Payload payload) {
            return Mono.empty();
          }
        };

    run();

    assertEquals(expected, output);
  }

  @Test
  public void requestResponse() throws Exception {
    main.requestResponse = true;
    main.input = "Hello";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Mono<Payload> requestResponse(Payload payload) {
            return Mono.just(reverse(bufferToString(payload)));
          }
        };

    expected.showOutput("olleH");

    run();

    assertEquals(expected, output);
  }

  private static String bufferToString(Payload payload) {
    return StandardCharsets.UTF_8.decode(payload.getData()).toString();
  }

  @Test
  public void requestResponseFromFile() throws Exception {
    main.requestResponse = true;
    main.input = "@src/test/resources/hello.text";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Mono<Payload> requestResponse(Payload payload) {
            return Mono.just(reverse(bufferToString(payload)));
          }
        };

    expected.showOutput("!elif a morf olleH");

    run();

    assertEquals(expected, output);
  }

  @Test
  public void requestResponseFromMissingFile() throws Exception {
    main.requestResponse = true;
    main.input = "@src/test/resources/goodbye.text";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Mono<Payload> requestResponse(Payload payload) {
            return Mono.just(reverse(bufferToString(payload)));
          }
        };

    expected.info("file not found: src/test/resources/goodbye.text");

    run();

    assertEquals(expected, output);
  }

  @Test
  public void requestResponseError() throws Exception {
    main.requestResponse = true;
    main.input = "Hello";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Mono<Payload> requestResponse(Payload payload) {
            return Mono.error(new ApplicationException("server failure"));
          }
        };

    expected.error("error from server", new ApplicationException("server failure"));

    run();

    assertEquals(expected, output);
  }

  @Test
  public void stream() throws Exception {
    main.stream = true;
    main.input = "Hello";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Flux<Payload> requestStream(Payload payload) {
            String s = bufferToString(payload);
            return Flux.range(1, 3).map(i -> reverse(s));
          }
        };

    expected.showOutput("olleH");
    expected.showOutput("olleH");
    expected.showOutput("olleH");

    run();

    assertEquals(expected, output);
  }

  @Test
  public void streamCompletedByFailure() throws Exception {
    main.stream = true;
    main.input = "Hello";

    requestHandler =
        new AbstractRSocket() {
          @Override
          public Flux<Payload> requestStream(Payload payload) {
            return Flux.range(1, 3)
                .map(i -> payload("i " + i))
                .concatWith(Mono.error(new ApplicationException("failed")));
          }
        };

    expected.showOutput("i 1");
    expected.showOutput("i 2");
    expected.showOutput("i 3");
    expected.error("error from server", new ApplicationException("failed"));

    run();

    assertEquals(expected, output);
  }

  private void run() throws Exception {
    connect();
    main.run(client).blockLast(Duration.ofSeconds(3));
  }

  public static Payload reverse(String s) {
    return payload(new StringBuilder(s).reverse().toString());
  }

  public static Payload payload(String data) {
    return new PayloadImpl(data);
  }
}
