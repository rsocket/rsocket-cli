/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivesocket.cli;

import io.reactivesocket.Payload;
import io.reactivesocket.internal.frame.ByteBufferUtil;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PayloadImpl implements Payload // some JDK shoutout
{
  private ByteBuffer data;
  private ByteBuffer metadata;

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

  public static ByteBuffer byteBufferFromUtf8String(final String data)
  {
    final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.wrap(bytes);
  }

  public boolean equals(Object obj)
  {
    final Payload rhs = (Payload) obj;

    return (data.equals(rhs.getData())) &&
        (metadata.equals(rhs.getMetadata()));
  }

  public ByteBuffer getData() {
    return data;
  }

  public ByteBuffer getMetadata() {
    return metadata;
  }
}