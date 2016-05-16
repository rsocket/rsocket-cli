/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivesocket.cli;

import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.DefaultReactiveSocket;
import io.reactivesocket.DuplexConnection;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.netty.tcp.client.ClientTcpDuplexConnection;
import io.reactivesocket.netty.websocket.client.ClientWebSocketDuplexConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import rx.RxReactiveStreams;

import static java.net.InetSocketAddress.createUnresolved;

/**
 * Simple command line tool to make a ReactiveSocket connection and send/receive elements.
 *
 * Currently limited in features, only supports a text/line based approach,
 * and only operates in channel mode, with all lines from System.in sent on that channel.
 */
public class Main {
  public static void main(String[] args) throws MalformedURLException, URISyntaxException {
    //String target = args.length > 0 ? args[0] : "tcp://localhost:7878";
    String target = args.length > 0 ? args[0] : "ws://localhost:8025/rs";
    URI uri = new URI(target);

    DuplexConnection duplexConnection = buildConnection(uri);

    ReactiveSocket client = DefaultReactiveSocket
        .fromClientConnection(duplexConnection, ConnectionSetupPayload.create("UTF-8", "UTF-8"),
            t -> t.printStackTrace());

    client.startAndWait();

    Publisher<Payload> inputStream = ObservableIO.systemInLines();

    Publisher<Payload> channelPublisher =
        client.requestChannel(inputStream);

    Subscriber<Payload> subscriber = new Subscriber<Payload>() {
      @Override public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
      }

      @Override public void onNext(Payload payload) {
        System.out.println("server responded: " + payload);
      }

      @Override public void onError(Throwable t) {
        t.printStackTrace();
      }

      @Override public void onComplete() {
        System.out.println("finished");
      }
    };
    channelPublisher.subscribe(subscriber);
  }

  private static DuplexConnection buildConnection(URI uri) {
    Publisher<? extends DuplexConnection> connection;
    if (uri.getScheme().equals("tcp")) {
      connection =
          ClientTcpDuplexConnection.create(createUnresolved(uri.getHost(), uri.getPort()),
              new NioEventLoopGroup());
    } else if (uri.getScheme().equals("ws")) {
      connection = ClientWebSocketDuplexConnection.create(uri, new NioEventLoopGroup());
    } else {
      throw new UnsupportedOperationException("uri unsupported: " + uri);
    }
    return RxReactiveStreams.toObservable(connection).toBlocking().single();
  }
}
