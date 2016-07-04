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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PayloadImpl implements Payload // some JDK shoutout
{
    private final ByteBuffer data;
    private final ByteBuffer metadata;

    public PayloadImpl(final String data) {
        this(data, null);
    }

    public PayloadImpl(final String data, final String metadata) {
        if (null == data) {
            this.data = ByteBuffer.allocate(0);
        } else {
            this.data = byteBufferFromUtf8String(data);
        }

        if (null == metadata) {
            this.metadata = ByteBuffer.allocate(0);
        } else {
            this.metadata = byteBufferFromUtf8String(metadata);
        }
    }

    public static ByteBuffer byteBufferFromUtf8String(final String data) {
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public ByteBuffer getData() {
        return data;
    }

    @Override
    public ByteBuffer getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PayloadImpl)) {
            return false;
        }

        PayloadImpl payload = (PayloadImpl) o;

        if (data != null ? !data.equals(payload.data) : payload.data != null) {
            return false;
        }
        if (metadata != null ? !metadata.equals(payload.metadata) : payload.metadata != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = data != null ? data.hashCode() : 0;
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        return result;
    }
}