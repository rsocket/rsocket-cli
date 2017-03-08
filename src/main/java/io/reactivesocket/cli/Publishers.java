/*
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivesocket.cli;

import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import io.reactivesocket.Payload;
import io.reactivesocket.util.PayloadImpl;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;

public final class Publishers {

    private Publishers() {
        // No instances.
    }

    /**
     * Return a publisher for consuming each line from the passed stream.
     *
     * @param inputStream to read.
     */
    public static Flux<Payload> lines(CharSource inputStream, Function<String, byte[]> metadataFn) {
        return splitInLines(inputStream)
                       .map(l -> (Payload) new PayloadImpl(l.getBytes(StandardCharsets.UTF_8), metadataFn.apply(l)));
    }

    public static Flux<String> splitInLines(CharSource inputStream) {
        return Flux.generate(c -> {
            try {
                inputStream.readLines(new LineProcessor<Void>() {
                    @Override
                    public boolean processLine(String line) {
                        c.next(line);
                        // TODO handle cancellation
                        return true;
                    }

                    @Override
                    public Void getResult() {
                        return null;
                    }
                });
                c.complete();
            } catch (IOException e) {
                c.error(e);
            }
        });
    }
}
