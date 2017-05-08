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
package io.rsocket.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.google.common.base.Throwables;
import java.util.Map;

public class MetadataUtil {

  public static byte[] encodeMetadataMap(Map<String, String> headerMap, String mimeType) {
    if (mimeType.equals("application/json")) {
      return MetadataUtil.jsonEncodeStringMap(headerMap);
    } else if (mimeType.equals("application/cbor")) {
      return MetadataUtil.cborEncodeStringMap(headerMap);
    } else {
      throw new UsageException("headers not supported with mimetype '" + mimeType + "'");
    }
  }

  public static final byte[] jsonEncodeStringMap(Map<String, String> headerMap) {
    ObjectMapper m = new ObjectMapper();

    try {
      return m.writeValueAsBytes(headerMap);
    } catch (JsonProcessingException e) {
      throw Throwables.propagate(e);
    }
  }

  public static byte[] cborEncodeStringMap(Map<String, String> headerMap) {
    ObjectMapper m = new ObjectMapper(new CBORFactory());

    try {
      return m.writeValueAsBytes(headerMap);
    } catch (JsonProcessingException e) {
      throw Throwables.propagate(e);
    }
  }
}
