/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.reactivesocket.cli;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;
import io.airlift.airline.ParseException;
import io.airlift.airline.SingleCommand;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.reactivesocket.AbstractReactiveSocket;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.client.KeepAliveProvider;
import io.reactivesocket.client.ReactiveSocketClient;
import io.reactivesocket.client.SetupProvider;
import io.reactivesocket.frame.ByteBufferUtil;
import io.reactivesocket.lease.DisabledLeaseAcceptingSocket;
import io.reactivesocket.reactivestreams.extensions.Px;
import io.reactivesocket.server.ReactiveSocketServer;
import io.reactivesocket.transport.TransportServer;
import io.reactivesocket.util.PayloadImpl;
import io.reactivex.Flowable;
import io.reactivex.netty.client.ClientState;
import org.agrona.LangUtil;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Completable;
import rx.Observable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static rx.RxReactiveStreams.toObservable;

/**
 * Simple command line tool to make a ReactiveSocket connection and send/receive elements.
 * <p>
 * Currently limited in features, only supports a text/line based approach.
 */
@Command(name = Main.NAME, description = "CLI for ReactiveSocket.")
public class Main {
    static final String NAME = "reactivesocket-cli";

    @Option(name = {"-h", "--help"}, description = "Display help information")
    public boolean help = false;

    @Option(name = "--sub", description = "Request Subscription")
    public boolean subscription;

    @Option(name = "--str", description = "Request Stream")
    public boolean stream;

    @Option(name = "--rr", description = "Request Response")
    public boolean requestResponse;

    @Option(name = "--fnf", description = "Fire and Forget")
    public boolean fireAndForget;

    @Option(name = "--channel", description = "Channel")
    public boolean channel;

    @Option(name = "--metadataPush", description = "Metadata Push")
    public boolean metadataPush;

    @Option(name = "--server", description = "Start server instead of client")
    public boolean serverMode = false;

    @Option(name = {"-i", "--input"}, description = "String input or @path/to/file")
    public String input;

    @Option(name = {"-m", "--metadata"}, description = "Metadata input string input or @path/to/file")
    public String metadata;

    @Option(name = "--setup", description = "String input or @path/to/file for metadata")
    public String setup;

    @Option(name = "--debug", description = "Debug Output")
    public boolean debug;

    @Option(name = "--ops", description = "Operation Count")
    public int operations = 1;

    @Option(name = "--timeout", description = "Timeout in seconds")
    public Long timeout;

    @Arguments(title = "target", description = "Endpoint URL", required = true)
    public List<String> arguments = new ArrayList<>();

    public ReactiveSocket client;

    public OutputHandler outputHandler;
    private TransportServer.StartedServer server;

    private Logger retainedLogger;

    private String cachedMetadata;

    public void run() throws IOException, URISyntaxException, InterruptedException {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", debug ? "debug" : "warn");

        retainedLogger = LoggerFactory.getLogger("");

        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);

        if (outputHandler == null) {
            outputHandler = new ConsoleOutputHandler();
        }

        try {
            URI uri = new URI(arguments.get(0));

            if (serverMode) {
                server = ReactiveSocketServer.create(ConnectionHelper.buildServerConnection(uri))
                    .start((setupPayload, reactiveSocket) -> new DisabledLeaseAcceptingSocket(createServerRequestHandler(setupPayload)));

                server.awaitShutdown();
            } else {
                SetupProvider setupProvider = SetupProvider.keepAlive(KeepAliveProvider.never()).disableLease();

                if (setup != null) {
                    setupProvider = setupProvider.setupPayload(parseSetupPayload());
                }

                client = Flowable
                    .fromPublisher(
                        ReactiveSocketClient
                            .create(ConnectionHelper.buildClientConnection(uri), setupProvider)
                            .connect())
                    .blockingFirst();

                Completable run = run(client);

                if (timeout != null) {
                    run.await(timeout, TimeUnit.SECONDS);
                } else {
                    run.await();
                }
            }
        } catch (Exception e) {
            outputHandler.error("error", e);
        } finally {
            ClientState.defaultEventloopGroup().shutdownGracefully();
        }
    }

    public Payload parseSetupPayload() {
        return new Payload() {
            @Override
            public ByteBuffer getMetadata() {
                return Frame.NULL_BYTEBUFFER;
            }

            @Override
            public ByteBuffer getData() {
                String source = null;

                if (setup.startsWith("@")) {
                    try {
                        source = Files.asCharSource(setupFile(), Charsets.UTF_8).read();
                    } catch (IOException e) {
                        LangUtil.rethrowUnchecked(e);
                    }

                } else {
                    source = setup;
                }

                return ByteBuffer.wrap(source.getBytes(Charsets.UTF_8));
            }
        };
    }

    private File setupFile() {
        File file = new File(input.substring(1));

        if (!file.isFile()) {
            throw new UsageException("setup file not found: " + file);
        }

        return file;
    }

    public ReactiveSocket createServerRequestHandler(ConnectionSetupPayload setupPayload) {
        LoggerFactory.getLogger(Main.class).debug("setup payload " + setupPayload);

        return new AbstractReactiveSocket() {
            @Override
            public Publisher<Void> fireAndForget(Payload payload) {
                outputHandler.showOutput(ByteBufferUtil.toUtf8String(payload.getData()));
                return Px.empty();
            }

            @Override
            public Publisher<Payload> requestResponse(Payload payload) {
                return handleIncomingPayload(payload);
            }

            @Override
            public Publisher<Payload> requestStream(Payload payload) {
                return handleIncomingPayload(payload);
            }

            @Override
            public Publisher<Payload> requestSubscription(Payload payload) {
                return handleIncomingPayload(payload);
            }

            @Override
            public Publisher<Payload> requestChannel(Publisher<Payload> payloads) {
                // TODO implement channel functionality
                return super.requestChannel(payloads);
            }

            @Override
            public Publisher<Void> metadataPush(Payload payload) {
                outputHandler.showOutput(ByteBufferUtil.toUtf8String(payload.getMetadata()));
                return Px.empty();
            }
        };
    }

    private Publisher<Payload> handleIncomingPayload(Payload payload) {
        outputHandler.showOutput(ByteBufferUtil.toUtf8String(payload.getData()));
        return inputPublisher();
    }

    public Completable run(ReactiveSocket client) {
        try {
            return runAllOperations(client);
        } catch (Exception e) {
            outputHandler.error("error", e);
        }

        return Completable.complete();
    }

    private Completable runAllOperations(ReactiveSocket client) {
        List<Completable> l = range(0, operations).mapToObj(i -> runSingleOperation(client)).collect(toList());

        return Completable.merge(l);
    }

    private Completable runSingleOperation(ReactiveSocket client) {
        if (fireAndForget) {
            return toObservable(client.fireAndForget(singleInputPayload())).toCompletable();
        } else if (metadataPush) {
            return toObservable(client.metadataPush(singleInputPayload())).toCompletable();
        }

        Observable<Payload> source;
        if (requestResponse) {
            source = toObservable(client.requestResponse(singleInputPayload()));
        } else if (subscription) {
            source = toObservable(client.requestSubscription(singleInputPayload()));
        } else if (stream) {
            source = toObservable(client.requestStream(singleInputPayload()));
        } else {
            // Defaults to channel for interactive mode.
            //TODO: We should have the mode as a group defaulting to channel?
            if (!channel) {
                outputHandler.info("Using request-channel interaction mode, choose an option to use a different mode.");
            }
            outputHandler.info("Type commands to send to the server.");
            source = toObservable(client.requestChannel(inputPublisher()));
        }

        return source.map(Payload::getData)
            .map(ByteBufferUtil::toUtf8String)
            .doOnNext(outputHandler::showOutput)
            .doOnError(e -> outputHandler.error("error from server", e))
            .onExceptionResumeNext(Observable.empty())
            .toCompletable();
    }


    private Publisher<Payload> inputPublisher() {
        CharSource is;

        if (input == null) {
            is = SystemInCharSource.INSTANCE;
        } else if (input.startsWith("@")) {
            is = Files.asCharSource(inputFile(), Charsets.UTF_8);
        } else {
            is = CharSource.wrap(input);
        }

        return ObservableIO.lines(is);
    }

    private String getMetadata() {
        synchronized (this) {
            if (cachedMetadata == null) {
                if (metadata == null) {
                    cachedMetadata = "";
                } else if (metadata.startsWith("@")) {
                    try {
                        cachedMetadata = Files.toString(new File(metadata.substring(1)), Charsets.UTF_8);
                    } catch (IOException e) {
                        LangUtil.rethrowUnchecked(e);
                    }
                } else {
                    cachedMetadata = metadata;
                }
            }
        }

        return cachedMetadata;
    }

    private PayloadImpl singleInputPayload() {
        String inputString;

        if (input == null) {
            Scanner in = new Scanner(System.in);
            inputString = in.nextLine();
        } else if (input.startsWith("@")) {
            try {
                inputString = Files.toString(inputFile(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UsageException(e.toString());
            }
        } else {
            inputString = input;
        }

        return new PayloadImpl(inputString, getMetadata());
    }

    private File inputFile() {
        File file = new File(input.substring(1));

        if (!file.isFile()) {
            throw new UsageException("file not found: " + file);
        }

        return file;
    }

    private static Main fromArgs(String... args) {
        SingleCommand<Main> cmd = SingleCommand.singleCommand(Main.class);
        try {
            return cmd.parse(args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            Help.help(cmd.getCommandMetadata());
            System.exit(-1);
            return null;
        }
    }

    public static void main(String... args) throws Exception {
        fromArgs(args).run();
    }
}
