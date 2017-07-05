package io.rsocket.cli.i9n;

import static org.junit.Assert.assertEquals;

import com.google.common.io.CharSource;
import io.rsocket.cli.Publishers;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PublishersTest {

  @Test(timeout = 2000)
  public void testSplitInLinesWithEOF() throws Exception {
    List<String> list = Publishers.splitInLines(CharSource.wrap("Hello")).collectList().block();

    assertEquals(Arrays.asList("Hello"), list);
  }

  @Test(timeout = 2000)
  public void testSplitInLinesWithNewLines() throws Exception {
    List<String> list =
        Publishers.splitInLines(CharSource.wrap("Hello\nHello1")).collectList().block();
    assertEquals(Arrays.asList("Hello", "Hello1"), list);
  }
}
