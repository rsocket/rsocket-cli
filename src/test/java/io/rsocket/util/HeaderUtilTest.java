package io.rsocket.util;

import static org.junit.Assert.assertEquals;

import io.rsocket.cli.util.HeaderUtil;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

public class HeaderUtilTest {
  @Test
  public void testSimpleValue() {
    assertEquals("hello", HeaderUtil.stringValue("hello"));
  }

  @Test
  public void testFileValue() throws URISyntaxException {
    assertEquals("value", HeaderUtil.stringValue("@" + path("value.txt")));
  }

  @Test
  public void headerMap() throws URISyntaxException {
    Map<String, String> map =
        HeaderUtil.headerMap(Arrays.asList("A: a", "B:b", "C: @" + path("value.txt")));

    assertEquals(3, map.size());
    assertEquals("a", map.get("A"));
    assertEquals("b", map.get("B"));
    assertEquals("value", map.get("C"));
  }

  @Test
  public void headerFileMap() throws URISyntaxException {
    Map<String, String> map = HeaderUtil.headerMap(Arrays.asList("@" + path("headers.txt")));

    assertEquals(2, map.size());
    assertEquals("a", map.get("A"));
    assertEquals("b", map.get("B"));
  }

  private String path(String resourceName) throws URISyntaxException {
    URL resource = getClass().getClassLoader().getResource(resourceName);
    return Paths.get(resource.toURI()).toFile().getPath();
  }
}
