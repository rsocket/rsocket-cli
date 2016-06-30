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
import org.reactivestreams.Publisher;
import rx.Observer;
import rx.RxReactiveStreams;
import rx.Scheduler;
import rx.internal.schedulers.CachedThreadScheduler;
import rx.internal.util.RxThreadFactory;
import rx.observables.SyncOnSubscribe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ThreadFactory;

public class ObservableIO {
    /**
     * Return a publisher for consuming each line from System.in.
     *
     * @param inputStream
     */
    public static Publisher<Payload> lines(InputStream inputStream) {
        SyncOnSubscribe<BufferedReader, Payload> s = new SyncOnSubscribe<BufferedReader, Payload>() {
            @Override
            protected BufferedReader generateState() {
                return new BufferedReader(new InputStreamReader(inputStream));
            }

            @Override
            protected BufferedReader next(BufferedReader state, Observer<? super Payload> observer) {
                try {
                    String line = state.readLine();

                    if (line == null) {
                        observer.onCompleted();
                    } else {
                        observer.onNext(new PayloadImpl(line, ""));
                    }
                } catch (IOException e) {
                    observer.onError(e);
                }

                return state;
            }
        };

        ThreadFactory tf = new RxThreadFactory("system.in");
        Scheduler scheduler = new CachedThreadScheduler(tf);
        rx.Observable<Payload> o = rx.Observable.create(s).subscribeOn(scheduler);

        return RxReactiveStreams.toPublisher(o);
    }
}
