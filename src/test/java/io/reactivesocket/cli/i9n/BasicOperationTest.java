package io.reactivesocket.cli.i9n;

import io.reactivesocket.AbstractReactiveSocket;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.cli.Main;
import io.reactivesocket.client.ReactiveSocketClient;
import io.reactivesocket.exceptions.ApplicationException;
import io.reactivesocket.frame.ByteBufferUtil;
import io.reactivesocket.lease.DisabledLeaseAcceptingSocket;
import io.reactivesocket.server.ReactiveSocketServer;
import io.reactivesocket.transport.TransportServer;
import io.reactivesocket.transport.local.LocalClient;
import io.reactivesocket.transport.local.LocalServer;
import io.reactivesocket.util.PayloadImpl;
import io.reactivex.Flowable;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static io.reactivesocket.client.KeepAliveProvider.never;
import static io.reactivesocket.client.SetupProvider.keepAlive;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

public class BasicOperationTest {
    private Main main = new Main();
    private TestOutputHandler output = new TestOutputHandler();
    private TransportServer.StartedServer server;
    private ReactiveSocket client;

    private final TestOutputHandler expected = new TestOutputHandler();

    private ReactiveSocket requestHandler = new AbstractReactiveSocket() {
    };

    private String testName;

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            testName = description.getMethodName();
        }
    };

    public void connect() {
        main.outputHandler = output;

        LocalServer localServer = LocalServer.create("test-local-server-"
                + testName);

        server = ReactiveSocketServer.create(localServer)
                .start((setup, sendingSocket) -> new DisabledLeaseAcceptingSocket(requestHandler));

        client = Flowable.fromPublisher(ReactiveSocketClient.create(LocalClient.create("test-local-server-" + testName),
                keepAlive(never()).disableLease()).connect()).blockingFirst();
    }

    @After
    public void shutdown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.shutdown();
            server.awaitShutdown(5, SECONDS);
        }
    }

    @Ignore("broken in reactivesocket-java for local")
    @Test
    public void metadataPush() throws Exception {
        main.metadataPush = true;
        main.input = "Hello";

        requestHandler = new AbstractReactiveSocket() {
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

        requestHandler = new AbstractReactiveSocket() {
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

        requestHandler = new AbstractReactiveSocket() {
            @Override
            public Mono<Payload> requestResponse(Payload payload) {
                return Mono.just(reverse(ByteBufferUtil.toUtf8String(payload.getData())));
            }
        };

        expected.showOutput("olleH");

        run();

        assertEquals(expected, output);
    }

    @Test
    public void requestResponseFromFile() throws Exception {
        main.requestResponse = true;
        main.input = "@src/test/resources/hello.text";

        requestHandler = new AbstractReactiveSocket() {
            @Override
            public Mono<Payload> requestResponse(Payload payload) {
                return Mono.just(reverse(ByteBufferUtil.toUtf8String(payload.getData())));
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

        requestHandler = new AbstractReactiveSocket() {
            @Override
            public Mono<Payload> requestResponse(Payload payload) {
                return Mono.just(reverse(ByteBufferUtil.toUtf8String(payload.getData())));
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

        requestHandler = new AbstractReactiveSocket() {
            @Override
            public Mono<Payload> requestResponse(Payload payload) {
                return Mono.error(new ApplicationException(payload("server failure")));
            }
        };

        expected.error("error from server", new ApplicationException(payload("server failure")));

        run();

        assertEquals(expected, output);
    }

    @Ignore("broken in reactivesocket-java for local")
    @Test
    public void stream() throws Exception {
        main.stream = true;
        main.input = "Hello";

        requestHandler = new AbstractReactiveSocket() {
            @Override
            public Flux<Payload> requestStream(Payload payload) {
                String s = ByteBufferUtil.toUtf8String(payload.getData());

                return Flux.range(1, 3).map(i -> reverse(s));
            }
        };

        expected.showOutput("olleH");
        expected.showOutput("olleH");
        expected.showOutput("olleH");

        // TODO filter next_complete?
        expected.showOutput("");

        run();

        assertEquals(expected, output);
    }

    @Test
    public void streamCompletedByFailure() throws Exception {
        main.stream = true;
        main.input = "Hello";

        requestHandler = new AbstractReactiveSocket() {
            @Override
            public Flux<Payload> requestStream(Payload payload) {
                return Flux.range(1, 3)
                        .map(i -> payload("i " + i))
                        .concatWith(Mono.error(new ApplicationException(new PayloadImpl("failed"))));
            }
        };

        expected.showOutput("i 1");
        expected.showOutput("i 2");
        expected.showOutput("i 3");
        expected.error("error from server", new ApplicationException(payload("failed")));

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
