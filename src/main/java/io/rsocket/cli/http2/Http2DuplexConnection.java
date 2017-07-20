package io.rsocket.cli.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.rsocket.DuplexConnection;
import io.rsocket.Frame;
import io.rsocket.exceptions.ConnectionCloseException;
import io.rsocket.transport.netty.RSocketLengthCodec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.reactivestreams.Publisher;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

public class Http2DuplexConnection implements DuplexConnection {
  private static final Logger log = Logger.getLogger(Http2DuplexConnection.class.getName());
  private final MonoProcessor<Void> onClose = MonoProcessor.create();
  private final DirectProcessor<Frame> receive = DirectProcessor.create();
  private final MyRSocketLengthCodec codec = new MyRSocketLengthCodec();

  private CompositeByteBuf existingData = Unpooled.compositeBuffer();

  private Stream.Listener responseListener =
      new Stream.Listener.Adapter() {
        @Override
        public void onData(Stream stream, DataFrame frame, Callback callback) {
          ByteBuffer fd = frame.getData();

          try {
            existingData.addComponent(true, Unpooled.copiedBuffer(fd));
            while (true) {
              ByteBuf sliceData = (ByteBuf) codec.decode(null, existingData);

              if (sliceData != null) {
                receive.onNext(Frame.from(sliceData));
              } else {
                break;
              }
            }
            callback.succeeded();
          } catch (Exception e) {
            callback.failed(e);
          }
        }

        @Override
        public void onHeaders(Stream stream, HeadersFrame frame) {
          log.fine(frame.getMetaData().toString());
          for (HttpField e : frame.getMetaData().getFields()) {
            log.fine(e.getName() + ": " + e.getValue());
          }

          MetaData.Response response = (MetaData.Response) frame.getMetaData();
          if (response.getStatus() == 200) {
            log.info("connected");
          } else {
            receive.onNext(
                Frame.Error.from(
                    0, new ConnectionCloseException("non 200 response: " + response.getStatus())));
            close().subscribe();
          }
        }
      };

  private Stream stream;

  @Override
  public Mono<Void> send(Publisher<Frame> frame) {
    return Flux.from(frame)
        .doOnNext(
            f -> {
              stream.data(
                  dataFrame(f),
                  new Callback() {
                    @Override
                    public void failed(Throwable x) {
                      receive.onError(x);
                      close().subscribe();
                    }
                  });
            })
        .then();
  }

  @Override
  public Flux<Frame> receive() {
    return receive;
  }

  @Override
  public double availability() {
    return stream.isClosed() ? 0.0 : 1.0;
  }

  @Override
  public Mono<Void> close() {
    return Mono.defer(
        () -> {
          // TODO listen to callback
          if (!stream.isClosed()) {
            stream.reset(
                new ResetFrame(stream.getId(), ErrorCode.STREAM_CLOSED_ERROR.code),
                new Callback() {
                  @Override
                  public void failed(Throwable x) {}
                });
          }
          onClose.onComplete();
          return onClose;
        });
  }

  @Override
  public Mono<Void> onClose() {
    return onClose;
  }

  public static Mono<Http2DuplexConnection> create(
      Session session, URI uri, Map<String, String> headers) {
    return Mono.create(
        s -> {
          Http2DuplexConnection c = new Http2DuplexConnection();

          headers.forEach((k, v) -> log.fine(k + ": " + v));

          session.newStream(
              headerFrame(uri, headers),
              new Promise<Stream>() {
                @Override
                public void succeeded(Stream result) {
                  c.stream = result;
                  s.success(c);
                }

                @Override
                public void failed(Throwable x) {
                  s.error(x);
                }
              },
              c.responseListener);
        });
  }

  private DataFrame dataFrame(Frame f) {
    return new DataFrame(stream.getId(), f.content().nioBuffer(), false);
  }

  private static HeadersFrame headerFrame(URI uri, Map<String, String> headers) {
    HttpFields requestFields = new HttpFields();
    for (Map.Entry<String, String> e : headers.entrySet()) {
      requestFields.put(e.getKey(), e.getValue());
    }

    MetaData.Request request =
        new MetaData.Request(
            "POST", new HttpURI(uri.toString()), HttpVersion.HTTP_2, requestFields);
    return new HeadersFrame(request, null, false);
  }

  public static class MyRSocketLengthCodec extends RSocketLengthCodec {
    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
      return super.decode(ctx, in);
    }
  }
}
