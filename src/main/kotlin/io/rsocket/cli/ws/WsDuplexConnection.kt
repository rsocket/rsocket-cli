package io.rsocket.cli.ws

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.rsocket.DuplexConnection
import io.rsocket.frame.FrameLengthCodec.FRAME_LENGTH_SIZE
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoProcessor
import reactor.core.publisher.UnicastProcessor
import reactor.kotlin.core.publisher.toFlux

class WsDuplexConnection : DuplexConnection {
  private var webSocket: WebSocket? = null

  private val frames = UnicastProcessor.create<ByteBuf>()

  private val onClose = MonoProcessor.create<Void>()

  override fun alloc(): ByteBufAllocator = ByteBufAllocator.DEFAULT

  override fun onClose(): Mono<Void> = onClose

  override fun receive(): Flux<ByteBuf> {
    return frames
  }

  override fun send(frames: Publisher<ByteBuf>): Mono<Void> {
    return frames.toFlux().doOnNext { byteBuf ->
      val byteString = byteBuf.nioBuffer().toByteString()
      byteBuf.release()
      webSocket!!.send(byteString)
    }.then()
  }

  override fun dispose() {
    println("dispose")

    webSocket!!.close(1000, "close")

    onClose.onComplete()
  }

  fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    println("closed $reason")
  }

  fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    println("closing $reason")
  }

  fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    println(t)
  }

  fun onMessage(webSocket: WebSocket, text: String) {
    TODO()
  }

  fun onMessage(webSocket: WebSocket, bytes: ByteString) {
    frames.onNext(Unpooled.wrappedBuffer(bytes.toByteArray()))
  }

  companion object {
    fun connect(client: OkHttpClient, request: Request): Mono<WsDuplexConnection> {
      return Mono.create {
        val conn = WsDuplexConnection()
        client.newWebSocket(request, object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            conn.webSocket = webSocket
            it.success(conn)
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (conn.webSocket != null) {
              conn.onFailure(webSocket, t, response)
            } else {
              it.error(t)
            }
          }

          override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            conn.onClosed(webSocket, code, reason)
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            conn.onClosing(webSocket, code, reason)
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            conn.onMessage(webSocket, text)
          }

          override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            conn.onMessage(webSocket, bytes)
          }
        })
      }
    }
  }
}
