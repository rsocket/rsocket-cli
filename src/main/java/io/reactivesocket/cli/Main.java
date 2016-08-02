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

import com.google.common.io.Files;
import io.airlift.airline.*;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.internal.frame.ByteBufferUtil;
import io.reactivex.netty.client.ClientState;
import org.reactivestreams.Publisher;
import rx.Completable;
import rx.Observable;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static rx.RxReactiveStreams.toObservable;

/**
 * Simple command line tool to make a ReactiveSocket connection and send/receive elements.
 * <p>
 * Currently limited in features, only supports a text/line based approach.
 */
@Command(name = Main.NAME, description = "CLI for ReactiveSocket.")
public class Main {
    static final String NAME = "reactivesocket-cli";

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

    @Option(name = {"-i", "--input"}, description = "String input or @path/to/file")
    public String input;

    @Option(name = "--debug", description = "Debug Output")
    public boolean debug;

    @Arguments(title = "target", description = "Endpoint URL", required = true)
    public List<String> arguments = new ArrayList<>();

    public ReactiveSocket client;

    public OutputHandler outputHandler;

    public void run() throws IOException, URISyntaxException, InterruptedException {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", debug ? "debug" : "warn");

        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);

        if (outputHandler == null) {
            outputHandler = new ConsoleOutputHandler();
        }

        try {
            URI uri = new URI(arguments.get(0));

            client = ConnectionHelper.buildConnection(uri);

            Completable run = run(client);
            run.await();
        } catch (Exception e) {
            outputHandler.error("error", e);
        } finally {
            ClientState.defaultEventloopGroup().shutdownGracefully();
        }
    }

    public Completable run(ReactiveSocket client) throws IOException {
        try {
            if (fireAndForget) {
                return toObservable(client.fireAndForget(singleInputPayload())).toCompletable();
            }

            Observable<Payload> source;
            if (requestResponse) {
                source = toObservable(client.requestResponse(singleInputPayload()));
            } else if (subscription) {
                source = toObservable(client.requestSubscription(singleInputPayload()));
            } else if (stream) {
                source = toObservable(client.requestStream(singleInputPayload()));
            } else {// Defaults to channel for interactive mode.
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
        } catch (Exception e) {
            outputHandler.error("error", e);
        }

        return Completable.complete();
    }

    private Publisher<Payload> inputPublisher() throws IOException {
        InputStream is;

        if (input == null) {
            is = System.in;
        } else if (input.startsWith("@")) {
            is = new FileInputStream(inputFile());
        } else {
            is = new ByteArrayInputStream(input.getBytes(Charset.defaultCharset()));
        }

        return ObservableIO.lines(is);
    }

    private PayloadImpl singleInputPayload() throws IOException {
        String inputString;

        if (input == null) {
            Scanner in = new Scanner(System.in);
            inputString = in.nextLine();
        } else if (input.startsWith("@")) {
            inputString = Files.toString(inputFile(), StandardCharsets.UTF_8);
        } else {
            inputString = input;
        }

        return new PayloadImpl(inputString, "");
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

    public static void main(String... args) throws IOException, URISyntaxException, InterruptedException {
        fromArgs(args).run();
    }
}
