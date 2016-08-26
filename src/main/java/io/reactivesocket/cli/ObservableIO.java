/**
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

import io.reactivesocket.Payload;
import io.reactivesocket.util.PayloadImpl;
import org.reactivestreams.Publisher;
import rx.observables.StringObservable;
import rx.schedulers.Schedulers;

import java.io.InputStream;
import java.io.InputStreamReader;

import static rx.RxReactiveStreams.toPublisher;

public final class ObservableIO {

    private ObservableIO() {
        // No instances.
    }

    /**
     * Return a publisher for consuming each line from System.in.
     *
     * @param inputStream to read.
     */
    public static Publisher<Payload> lines(InputStream inputStream) {
        return toPublisher(StringObservable.using(() -> new InputStreamReader(inputStream),
                stream -> StringObservable.from(stream))
                .<Payload>map(PayloadImpl::new)
                .subscribeOn(Schedulers.io()));
    }
}
