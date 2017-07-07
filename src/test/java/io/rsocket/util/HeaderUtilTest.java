package io.rsocket.util;

import io.rsocket.cli.util.HeaderUtil;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HeaderUtilTest {
  @Test
  public void testSimpleValue() {
    assertEquals("hello", HeaderUtil.stringValue("hello"));
  }

  @Test
  public void testFileValue() {
    assertEquals("value", HeaderUtil.stringValue("@src/test/resources/value.txt"));
  }

  @Test
  public void headerMap() {
    Map<String, String> map =
        HeaderUtil.headerMap(Arrays.asList("A: a", "B:b", "C: @src/test/resources/value.txt"));

    assertEquals(3, map.size());
    assertEquals("a", map.get("A"));
    assertEquals("b", map.get("B"));
    assertEquals("value", map.get("C"));
  }

  @Test
  public void headerFileMap() {
    Map<String, String> map =
        HeaderUtil.headerMap(Arrays.asList("@src/test/resources/headers.txt"));

    assertEquals(2, map.size());
    assertEquals("a", map.get("A"));
    assertEquals("b", map.get("B"));
  }
}
