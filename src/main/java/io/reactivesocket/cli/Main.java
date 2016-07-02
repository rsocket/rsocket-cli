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

import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;
import io.airlift.airline.ParseException;
import io.airlift.airline.SingleCommand;
import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.DefaultReactiveSocket;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.internal.frame.ByteBufferUtil;
import io.reactivesocket.transport.tcp.client.TcpReactiveSocketConnector;
import io.reactivesocket.transport.websocket.client.ClientWebSocketDuplexConnection;
import io.reactivex.netty.client.ClientState;
import io.reactivex.netty.protocol.tcp.client.TcpClient;
import org.reactivestreams.Publisher;
import rx.Completable;
import rx.Observable;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static rx.RxReactiveStreams.*;

/**
 * Simple command line tool to make a ReactiveSocket connection and send/receive elements.
 * <p>
 * Currently limited in features, only supports a text/line based approach, and only operates in
 * channel mode, with all lines from System.in sent on that channel.
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

    @Option(name = {"-i", "--input"}, description = "Input File")
    public String input;

    @Option(name = "--debug", description = "Debug Output")
    public boolean debug;

    @Arguments(title = "target", description = "Endpoint URL", required = true)
    public List<String> arguments = new ArrayList<>();

    public void run() throws IOException, URISyntaxException, InterruptedException {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", debug ? "debug" : "warn");

        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);

        try {
            URI uri = new URI(arguments.get(0));

            ReactiveSocket client = buildConnection(uri);

            Completable run = run(client);
            run.await();
        } finally {
            ClientState.defaultEventloopGroup().shutdownGracefully();
        }
    }

    private Completable run(ReactiveSocket client) throws FileNotFoundException {

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
                System.out.println("Using request-channel interaction mode, choose an option to use a different mode.");
            }
            System.out.println("Type commands to send to the server.");
            source = toObservable(client.requestChannel(inputPublisher()));
        }

        return source.map(Payload::getData)
                     .map(ByteBufferUtil::toUtf8String)
                     .doOnNext(System.out::println)
                     .toCompletable();
    }

    private Publisher<Payload> inputPublisher() throws FileNotFoundException {
        InputStream is = input != null ? new FileInputStream(input) : System.in;
        return ObservableIO.lines(is);
    }

    private static PayloadImpl singleInputPayload() {
        return new PayloadImpl(System.console().readLine(), "");
    }

    private static ReactiveSocket buildConnection(URI uri) {
        ConnectionSetupPayload setupPayload = ConnectionSetupPayload.create("", "");

        if ("tcp".equals(uri.getScheme())) {
            Function<SocketAddress, TcpClient<ByteBuf, ByteBuf>> clientFactory =
                    socketAddress -> TcpClient.newClient(socketAddress).enableWireLogging("rs",
                            LogLevel.INFO);
            return toObservable(
                    TcpReactiveSocketConnector.create(setupPayload, Throwable::printStackTrace, clientFactory)
                            .connect(new InetSocketAddress(uri.getHost(), uri.getPort()))).toSingle()
                    .toBlocking()
                    .value();
        } else if ("ws".equals(uri.getScheme())) {
            ClientWebSocketDuplexConnection duplexConnection = toObservable(
                    ClientWebSocketDuplexConnection.create(
                            InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()), "/rs",
                            ClientState.defaultEventloopGroup())).toBlocking().last();

            return DefaultReactiveSocket.fromClientConnection(duplexConnection, setupPayload,
                    Throwable::printStackTrace);
        } else {
            throw new UnsupportedOperationException("uri unsupported: " + uri);
        }
    }

    private static Main fromArgs(String... args) {
        SingleCommand<Main> cmd = SingleCommand.singleCommand(Main.class);
        try {
            return cmd.parse(args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            Help.help(cmd.getCommandMetadata());
            System.exit(-1);
            return null;
        }
    }

    public static void main(String... args) throws IOException, URISyntaxException, InterruptedException {
        fromArgs(args).run();
    }
}
