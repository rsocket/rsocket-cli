package io.rsocket.cli.http2

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.rsocket.DuplexConnection
import io.rsocket.Frame
import io.rsocket.exceptions.ConnectionCloseException
import io.rsocket.transport.netty.RSocketLengthCodec
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.http.MetaData
import org.eclipse.jetty.http2.ErrorCode
import org.eclipse.jetty.http2.api.Session
import org.eclipse.jetty.http2.api.Stream
import org.eclipse.jetty.http2.frames.DataFrame
import org.eclipse.jetty.http2.frames.HeadersFrame
import org.eclipse.jetty.http2.frames.ResetFrame
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.Promise
import org.reactivestreams.Publisher
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoProcessor
import java.net.URI
import java.util.logging.Logger

class Http2DuplexConnection : DuplexConnection {
  private val onClose = MonoProcessor.create<Void>()
  private val receive = DirectProcessor.create<Frame>()
  private val codec = MyRSocketLengthCodec()

  private val existingData = Unpooled.compositeBuffer()

  private val responseListener = object : Stream.Listener.Adapter() {
    override fun onData(stream: Stream?, frame: DataFrame?, callback: Callback) {
      val fd = frame!!.data

      try {
        existingData.addComponent(true, Unpooled.copiedBuffer(fd))
        while (true) {
          val sliceData = codec.decode(null, existingData)

          if (sliceData != null) {
            receive.onNext(Frame.from(sliceData))
          } else {
            break
          }
        }
        callback.succeeded()
      } catch (e: Exception) {
        callback.failed(e)
      }

    }

    override fun onHeaders(stream: Stream?, frame: HeadersFrame?) {
      log.fine(frame!!.metaData.toString())
      for (e in frame.metaData.fields) {
        log.fine(e.name + ": " + e.value)
      }

      val response = frame.metaData as MetaData.Response
      if (response.status == 200) {
        log.info("connected")
      } else {
        receive.onNext(
            Frame.Error.from(
                0, ConnectionCloseException("non 200 response: " + response.status)))
        close().subscribe()
      }
    }
  }

  private var stream: Stream? = null

  override fun send(frame: Publisher<Frame>): Mono<Void> {
    return Flux.from(frame)
        .doOnNext { f ->
          stream!!.data(
              dataFrame(f),
              object : Callback {
                override fun failed(x: Throwable?) {
                  receive.onError(x!!)
                  close().subscribe()
                }
              })
        }
        .then()
  }

  override fun receive(): Flux<Frame> = receive

  override fun availability(): Double = if (stream!!.isClosed) 0.0 else 1.0

  override fun close(): Mono<Void> = Mono.defer {
    // TODO listen to callback
    if (!stream!!.isClosed) {
      stream!!.reset(
          ResetFrame(stream!!.id, ErrorCode.STREAM_CLOSED_ERROR.code),
          object : Callback {
            override fun failed(x: Throwable?) {}
          })
    }
    onClose.onComplete()
    onClose
  }

  override fun onClose(): Mono<Void> = onClose

  private fun dataFrame(f: Frame): DataFrame =
      DataFrame(stream!!.id, f.content().nioBuffer(), false)

  class MyRSocketLengthCodec : RSocketLengthCodec() {
    @Throws(Exception::class)
    public override fun decode(ctx: ChannelHandlerContext?, `in`: ByteBuf): ByteBuf? {
      return super.decode(ctx, `in`) as ByteBuf?
    }
  }

  companion object {
    private val log = Logger.getLogger(Http2DuplexConnection::class.java.name)

    fun create(
        session: Session, uri: URI, headers: Map<String, String>): Mono<Http2DuplexConnection> {
      return Mono.create { s ->
        val c = Http2DuplexConnection()

        headers.forEach { k, v -> log.fine(k + ": " + v) }

        session.newStream(
            headerFrame(uri, headers),
            object : Promise<Stream> {
              override fun succeeded(result: Stream?) {
                c.stream = result
                s.success(c)
              }

              override fun failed(x: Throwable?) {
                s.error(x!!)
              }
            },
            c.responseListener)
      }
    }

    private fun headerFrame(uri: URI, headers: Map<String, String>): HeadersFrame {
      val requestFields = HttpFields()
      for ((key, value) in headers) {
        requestFields.put(key, value)
      }

      val request = MetaData.Request(
          "POST", HttpURI(uri.toString()), HttpVersion.HTTP_2, requestFields)
      return HeadersFrame(request, null, false)
    }
  }
}
