package io.rsocket.cli.http2

import io.rsocket.DuplexConnection
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.TransportHeaderAware
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
import java.util.function.Supplier

class Http2ClientTransport @JvmOverloads constructor(
        private val uri: URI,
        private var transportHeadersFn: () -> Map<String, String> = { mutableMapOf() }) : ClientTransport, TransportHeaderAware {

    override fun setTransportHeaders(transportHeaders: Supplier<Map<String, String>>?) {
        this.transportHeadersFn = { transportHeaders?.get() ?: mapOf() }
    }

    override fun connect(): Mono<DuplexConnection> =
            createSession().flatMap { s -> Http2DuplexConnection.create(s, uri, transportHeadersFn()) }

    private fun createSession(): Mono<Session> = Mono.create { c ->
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

    private fun daemonClientScheduler(): ScheduledExecutorScheduler =
            ScheduledExecutorScheduler("jetty-scheduler", true)

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
        init {
            Log.setLog(JavaUtilLog())
        }
    }
}
