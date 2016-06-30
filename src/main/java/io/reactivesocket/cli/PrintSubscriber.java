/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.reactivesocket.cli;

import io.reactivesocket.Payload;
import io.reactivesocket.internal.frame.ByteBufferUtil;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

class PrintSubscriber implements Subscriber<Payload> {
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(Payload payload) {
        System.out.println(ByteBufferUtil.toUtf8String(payload.getData()));
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
        latch.countDown();
    }

    @Override
    public void onComplete() {
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }
}
