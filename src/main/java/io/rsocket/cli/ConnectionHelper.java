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
package io.rsocket.cli;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.tcp.TcpClient;
import reactor.ipc.netty.tcp.TcpServer;

import java.net.URI;

public class ConnectionHelper {
    public static ClientTransport buildClientConnection(URI uri) {
        if ("tcp".equals(uri.getScheme())) {
            return TcpClientTransport.create(TcpClient.create(uri.getHost(), uri.getPort()));
        } else if ("ws".equals(uri.getScheme())) {
            return WebsocketClientTransport.create(uri.getHost(), wsPort(uri));
        } else if ("wss".equals(uri.getScheme())) {
            HttpClient httpClient =
                    HttpClient.create(options -> options.sslSupport().connect(uri.getHost(), wssPort(uri)));
            return WebsocketClientTransport.create(httpClient);
        } else {
            throw new UnsupportedOperationException("uri unsupported: " + uri);
        }
    }

    public static ServerTransport buildServerConnection(URI uri) {
        if ("tcp".equals(uri.getScheme())) {
            return TcpServerTransport.create(TcpServer.create(uri.getHost(), uri.getPort()));
        } else if ("ws".equals(uri.getScheme())) {
            return WebsocketServerTransport.create(uri.getHost(), wsPort(uri));
        } else if ("wss".equals(uri.getScheme())) {
            throw new UnsupportedOperationException("server not supported for WSS");
        } else {
            throw new UnsupportedOperationException("uri unsupported: " + uri);
        }
    }

    private static int wsPort(URI uri) {
        return uri.getPort() == -1 ? 80 : uri.getPort();
    }

    private static int wssPort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }
}
