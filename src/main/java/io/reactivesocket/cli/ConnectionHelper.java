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

import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.DefaultReactiveSocket;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.transport.tcp.client.TcpReactiveSocketConnector;
import io.reactivesocket.transport.websocket.client.ClientWebSocketDuplexConnection;
import io.reactivex.netty.client.ClientState;
import io.reactivex.netty.protocol.tcp.client.TcpClient;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.function.Function;

import static rx.RxReactiveStreams.toObservable;

public class ConnectionHelper {
    public static ReactiveSocket buildConnection(URI uri) {
        ConnectionSetupPayload setupPayload = ConnectionSetupPayload.create("", "", ConnectionSetupPayload.NO_FLAGS);

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
}
