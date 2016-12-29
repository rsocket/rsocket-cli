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

import io.reactivesocket.transport.TransportClient;
import io.reactivesocket.transport.TransportServer;
import io.reactivesocket.transport.tcp.client.TcpTransportClient;
import io.reactivesocket.transport.tcp.server.TcpTransportServer;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.URI;

public class ConnectionHelper {
    public static TransportClient buildClientConnection(URI uri) {
        if ("tcp".equals(uri.getScheme())) {
            return TcpTransportClient.create(new InetSocketAddress(uri.getHost(), uri.getPort())).logReactiveSocketFrames("rs", Level.INFO);
        } else {
            throw new UnsupportedOperationException("uri unsupported: " + uri);
        }
    }

    public static TransportServer buildServerConnection(URI uri) {
        if ("tcp".equals(uri.getScheme())) {
            return TcpTransportServer.create(new InetSocketAddress(uri.getHost(), uri.getPort())).logReactiveSocketFrames("rs", Level.INFO);
        } else {
            throw new UnsupportedOperationException("uri unsupported: " + uri);
        }
    }
}
