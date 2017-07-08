package io.rsocket.cli.util;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import io.rsocket.cli.Publishers;
import io.rsocket.cli.UsageException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.rsocket.cli.util.FileUtil.expectedFile;

// TODO handle duplicate header keys
public class HeaderUtil {
  public static Map<String, String> headerMap(List<String> headers) {
    Map<String, String> headerMap = new LinkedHashMap<>();

    if (headers != null) {
      for (String header : headers) {
        if (header.startsWith("@")) {
          headerMap.putAll(headerFileMap(header));
        } else {
          String[] parts = header.split(":", 2);
          // TODO: consider better strategy than simple trim
          String name = parts[0].trim();
          String value = stringValue(parts[1].trim());
          headerMap.put(name, value);
        }
      }
    }
    return headerMap;
  }

  private static Map<? extends String, ? extends String> headerFileMap(String input) {
    CharSource is = Files.asCharSource(inputFile(input), Charsets.UTF_8);
    return headerMap(Publishers.splitInLines(is).collectList().block());
  }

  public static String stringValue(String source) {
    if (source.startsWith("@")) {
      try {
        return Files.toString(inputFile(source), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UsageException(e.toString());
      }
    } else {
      return source;
    }
  }

  public static File inputFile(String path) {
    return expectedFile(path.substring(1));
  }
}
