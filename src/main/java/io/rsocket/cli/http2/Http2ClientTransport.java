package io.rsocket.cli.http2;

import io.rsocket.DuplexConnection;
import io.rsocket.cli.HeaderAware;
import io.rsocket.transport.ClientTransport;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import reactor.core.publisher.Mono;

public class Http2ClientTransport implements ClientTransport, HeaderAware {
  private static final Logger log = Logger.getLogger(Http2DuplexConnection.class.getName());

  static {
    Log.setLog(new JavaUtilLog());
  }

  private final URI uri;
  // assume simplified header model
  private Map<String, String> headers;

  public Http2ClientTransport(URI uri) {
    this(uri, Collections.emptyMap());
  }

  public Http2ClientTransport(URI uri, Map<String, String> headers) {
    this.uri = uri;
    this.headers = headers;
  }

  @Override
  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  @Override
  public Mono<DuplexConnection> connect() {
    return createSession().flatMap(s -> Http2DuplexConnection.create(s, uri, headers));
  }

  private Mono<Session> createSession() {
    return Mono.create(
        c -> {
          HTTP2Client client = new HTTP2Client();
          client.setExecutor(daemonClientExecutor());
          client.setScheduler(daemonClientScheduler());
          SslContextFactory sslContextFactory = null;
          if (uri.getScheme().equals("https")) {
            sslContextFactory = new SslContextFactory();
            client.addBean(sslContextFactory);
          }

          try {
            client.start();

              int port = getPort();
              System.out.println(port);
              client.connect(
                sslContextFactory,
                new InetSocketAddress(uri.getHost(), port),
                new ServerSessionListener.Adapter(),
                new Promise<Session>() {
                  @Override
                  public void succeeded(Session result) {
                    c.success(result);
                  }

                  @Override
                  public void failed(Throwable x) {
                    c.error(x);
                  }
                });
          } catch (Exception e) {
            c.error(e);
          }
        });
  }

  private ScheduledExecutorScheduler daemonClientScheduler() {
    ScheduledExecutorScheduler scheduler = new ScheduledExecutorScheduler("jetty-scheduler", true);
    return scheduler;
  }

  private QueuedThreadPool daemonClientExecutor() {
    QueuedThreadPool executor = new QueuedThreadPool();
    executor.setDaemon(true);
    return executor;
  }

  private int getPort() {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }

    return "https".equals(uri.getScheme()) ? 443 : 80;
  }
}
