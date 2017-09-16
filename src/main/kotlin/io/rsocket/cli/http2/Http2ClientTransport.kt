package io.rsocket.cli.http2

import io.rsocket.DuplexConnection
import io.rsocket.cli.HeaderAware
import io.rsocket.transport.ClientTransport
import org.eclipse.jetty.http.HttpScheme
import org.eclipse.jetty.http2.api.Session
import org.eclipse.jetty.http2.api.server.ServerSessionListener
import org.eclipse.jetty.http2.client.HTTP2Client
import org.eclipse.jetty.util.Promise
import org.eclipse.jetty.util.log.JavaUtilLog
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.net.URI
import java.util.logging.Logger

class Http2ClientTransport @JvmOverloads constructor(private val uri: URI, // assume simplified header model
                                                     private var headers: Map<String, String> = emptyMap<String, String>()) : ClientTransport, HeaderAware {

  override fun setHeaders(headers: Map<String, String>) {
    this.headers = headers
  }

  override fun connect(): Mono<DuplexConnection> {
    return createSession().flatMap { s -> Http2DuplexConnection.create(s, uri, headers) }
  }

  private fun createSession(): Mono<Session> {
    return Mono.create { c ->
      val client = HTTP2Client()
      client.executor = daemonClientExecutor()
      client.scheduler = daemonClientScheduler()
      var sslContextFactory: SslContextFactory? = null
      if (HttpScheme.HTTPS.`is`(uri.scheme)) {
        sslContextFactory = SslContextFactory()
        client.addBean(sslContextFactory)
      }

      try {
        client.start()

        client.connect(
            sslContextFactory,
            InetSocketAddress(uri.host, port),
            ServerSessionListener.Adapter(),
            object : Promise<Session> {
              override fun succeeded(result: Session?) {
                c.success(result)
              }

              override fun failed(x: Throwable?) {
                c.error(x!!)
              }
            })
      } catch (e: Exception) {
        c.error(e)
      }
    }
  }

  private fun daemonClientScheduler(): ScheduledExecutorScheduler {
    return ScheduledExecutorScheduler("jetty-scheduler", true)
  }

  private fun daemonClientExecutor(): QueuedThreadPool {
    val executor = QueuedThreadPool()
    executor.isDaemon = true
    return executor
  }

  private val port: Int
    get() {
      if (uri.port != -1) {
        return uri.port
      }

      return if (HttpScheme.HTTPS.`is`(uri.scheme)) 443 else 80
    }

  companion object {
    private val log = Logger.getLogger(Http2DuplexConnection::class.java!!.getName())

    init {
      Log.setLog(JavaUtilLog())
    }
  }
}
