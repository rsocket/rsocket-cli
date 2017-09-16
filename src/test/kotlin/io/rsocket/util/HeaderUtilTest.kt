package io.rsocket.util

import org.junit.Assert.assertEquals

import io.rsocket.cli.util.HeaderUtil
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Paths
import java.util.Arrays
import org.junit.Test

class HeaderUtilTest {
  @Test
  fun testSimpleValue() {
    assertEquals("hello", HeaderUtil.stringValue("hello"))
  }

  @Test
  @Throws(URISyntaxException::class)
  fun testFileValue() {
    assertEquals("value", HeaderUtil.stringValue("@" + path("value.txt")))
  }

  @Test
  @Throws(URISyntaxException::class)
  fun headerMap() {
    val map = HeaderUtil.headerMap(Arrays.asList("A: a", "B:b", "C: @" + path("value.txt")))

    assertEquals(3, map.size.toLong())
    assertEquals("a", map["A"])
    assertEquals("b", map["B"])
    assertEquals("value", map["C"])
  }

  @Test
  @Throws(URISyntaxException::class)
  fun headerFileMap() {
    val map = HeaderUtil.headerMap(Arrays.asList("@" + path("headers.txt")))

    assertEquals(2, map.size.toLong())
    assertEquals("a", map["A"])
    assertEquals("b", map["B"])
  }

  @Throws(URISyntaxException::class)
  private fun path(resourceName: String): String {
    val resource = javaClass.getClassLoader().getResource(resourceName)
    return Paths.get(resource!!.toURI()).toFile().getPath()
  }
}
