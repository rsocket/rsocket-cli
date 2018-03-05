package io.rsocket.cli

import reactor.core.publisher.Flux

// Temp shitty workaround
fun <T> Flux<T>.takeN(request: Int) =
        if (request < Int.MAX_VALUE) this.limitRate(request).take(request.toLong()) else this
