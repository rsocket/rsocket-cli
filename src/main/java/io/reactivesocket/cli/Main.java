/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.reactivesocket.cli;

import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.DefaultReactiveSocket;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.transport.tcp.client.TcpReactiveSocketConnector;
import io.reactivesocket.transport.websocket.client.ClientWebSocketDuplexConnection;
import io.reactivex.netty.client.ClientState;
import io.reactivex.netty.protocol.tcp.client.TcpClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import rx.RxReactiveStreams;

/**
 * Simple command line tool to make a ReactiveSocket connection and send/receive elements.
 *
 * Currently limited in features, only supports a text/line based approach, and only operates in
 * channel mode, with all lines from System.in sent on that channel.
 */
public class Main {
  public static void main(String[] args) throws IOException, URISyntaxException {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");

    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);

    try {
      String target = args.length > 0 ? args[0] : "tcp://localhost:9898";
      
      URI uri = new URI(target);

      ReactiveSocket client = buildConnection(uri);

      Publisher<Payload> channelPublisher =
          client.requestResponse(new PayloadImpl("hello", ""));

      Subscriber<Payload> subscriber = new Subscriber<Payload>() {
        @Override public void onSubscribe(Subscription s) {
          s.request(Long.MAX_VALUE);
        }

        @Override public void onNext(Payload payload) {
          System.out.println("server responded: " + payload.getData().remaining());
        }

        @Override public void onError(Throwable t) {
          t.printStackTrace();
        }

        @Override public void onComplete() {
          System.out.println("finished");
        }
      };
      channelPublisher.subscribe(subscriber);

      channelPublisher =
          client.requestResponse(new PayloadImpl("hello", ""));
      channelPublisher.subscribe(subscriber);

      System.in.read();
    } finally {
      ClientState.defaultEventloopGroup().shutdownGracefully();
    }
  }

  private static ReactiveSocket buildConnection(URI uri) {
    ConnectionSetupPayload setupPayload = ConnectionSetupPayload.create("", "");

    if (uri.getScheme().equals("tcp")) {
      Function<SocketAddress, TcpClient<ByteBuf, ByteBuf>> clientFactory =
          socketAddress -> TcpClient.newClient(socketAddress).enableWireLogging("rs",
              LogLevel.INFO);
      return RxReactiveStreams.toObservable(
          TcpReactiveSocketConnector.create(setupPayload, Throwable::printStackTrace, clientFactory)
              .connect(new InetSocketAddress(uri.getHost(), uri.getPort()))).toSingle()
          .toBlocking()
          .value();
    } else if (uri.getScheme().equals("ws")) {
      ClientWebSocketDuplexConnection duplexConnection = RxReactiveStreams.toObservable(
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
