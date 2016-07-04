package io.reactivesocket.cli.i9n;

import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.RequestHandler;
import io.reactivesocket.cli.Main;
import io.reactivesocket.cli.PayloadImpl;
import io.reactivesocket.internal.frame.ByteBufferUtil;
import io.reactivesocket.local.LocalClientReactiveSocketConnector;
import io.reactivesocket.local.LocalServerReactiveSocketConnector;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import rx.Observable;
import rx.RxReactiveStreams;

import static io.reactivesocket.util.Unsafe.toSingleFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

public class BasicOperationTest {
    private Main main = new Main();
    private TestOutputHandler output = new TestOutputHandler();
    private ReactiveSocket server;
    private ReactiveSocket client;

    private TestOutputHandler expected = new TestOutputHandler();

    private RequestHandler.Builder requestHandlerBuilder = new RequestHandler.Builder();

    private String testName;

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            testName = description.getMethodName();
        }
    };

    public void connect() throws Exception {
        main.outputHandler = output;

        LocalServerReactiveSocketConnector.Config serverConfig =
                new LocalServerReactiveSocketConnector.Config(testName, (payload, rs) -> requestHandlerBuilder.build());
        server = toSingleFuture(LocalServerReactiveSocketConnector.INSTANCE.connect(serverConfig)).get(5, SECONDS);

        LocalClientReactiveSocketConnector.Config clientConfig =
                new LocalClientReactiveSocketConnector.Config(testName, "text", "text");
        client = toSingleFuture(LocalClientReactiveSocketConnector.INSTANCE.connect(clientConfig)).get(5, SECONDS);
    }

    @After
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void fireAndForget() throws Exception {
        main.fireAndForget = true;
        main.input = "Hello";

        requestHandlerBuilder.withRequestResponse(payload -> subscriber -> subscriber.onComplete()).build();

        run();

        assertEquals(expected, output);
    }

    @Test
    public void requestResponse() throws Exception {
        main.requestResponse = true;
        main.input = "Hello";

        requestHandlerBuilder.withRequestResponse(payload ->
                RxReactiveStreams.toPublisher(Observable.just(reverse(payload)))).build();

        expected.showOutput("olleH");

        run();

        assertEquals(expected, output);
    }

    @Test
    public void requestResponseFromFile() throws Exception {
        main.requestResponse = true;
        main.input = "@src/test/resources/hello.text";

        requestHandlerBuilder.withRequestResponse(payload ->
                RxReactiveStreams.toPublisher(Observable.just(reverse(payload)))).build();

        expected.showOutput("!elif a morf olleH");

        run();

        assertEquals(expected, output);
    }

    @Test
    public void requestResponseFromMissingFile() throws Exception {
        main.requestResponse = true;
        main.input = "@src/test/resources/goodbye.text";

        requestHandlerBuilder.withRequestResponse(payload ->
                RxReactiveStreams.toPublisher(Observable.just(reverse(payload)))).build();

        expected.info("file not found: src/test/resources/goodbye.text");

        run();

        assertEquals(expected, output);
    }

    @Test
    public void requestResponseError() throws Exception {
        main.requestResponse = true;
        main.input = "Hello";

        requestHandlerBuilder.withRequestResponse(payload ->
                RxReactiveStreams.toPublisher(Observable.error(new Exception("server failure")))).build();

        expected.error("error from server", new RuntimeException("server failure"));

        run();

        assertEquals(expected, output);
    }

    @Test
    public void stream() throws Exception {
        main.stream = true;
        main.input = "Hello";

        requestHandlerBuilder.withRequestStream(payload ->
                RxReactiveStreams.toPublisher(Observable.range(1, 3).map(i -> reverse(payload)))).build();

        expected.showOutput("olleH");
        expected.showOutput("olleH");
        expected.showOutput("olleH");

        run();

        assertEquals(expected, output);
    }

    @Test
    public void subscriptionCompletedByFailure() throws Exception {
        main.subscription = true;
        main.input = "Hello";

        Observable<Payload> observableOf3 = Observable.range(1, 3).map(i -> payload("i " + i));
        Observable<Payload> failed = Observable.error(new Exception("failed"));

        requestHandlerBuilder.withRequestSubscription(payload ->
                RxReactiveStreams.toPublisher(observableOf3.concatWith(failed))).build();

        expected.showOutput("i 1");
        expected.showOutput("i 2");
        expected.showOutput("i 3");
        expected.error("error from server", new RuntimeException("failed"));

        run();

        assertEquals(expected, output);
    }

    private void run() throws Exception {
        connect();
        main.run(client).await(5, SECONDS);
    }

    public static Payload reverse(Payload payload) {
        return payload(new StringBuilder(ByteBufferUtil.toUtf8String(payload.getData())).reverse().toString());
    }

    public static Payload payload(String data) {
        return new PayloadImpl(data);
    }
}
