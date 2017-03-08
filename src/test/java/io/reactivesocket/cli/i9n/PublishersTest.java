package io.reactivesocket.cli.i9n;

import com.google.common.io.CharSource;
import io.reactivesocket.cli.Publishers;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.reactivestreams.Subscriber;

public class PublishersTest {

    @Test(timeout = 2000)
    public void testSplitInLinesWithEOF() throws Exception {
        TestSubscriber<String> sub = TestSubscriber.create();
        Publishers.splitInLines(CharSource.wrap("Hello")).subscribe(sub);
        sub.await().assertNoErrors().assertValue("Hello");
    }

    @Test(timeout = 2000)
    public void testSplitInLinesWithNewLines() throws Exception {
        TestSubscriber<String> sub = TestSubscriber.create();
        Publishers.splitInLines(CharSource.wrap("Hello\nHello1")).subscribe(sub);
        sub.await().assertNoErrors().assertValues("Hello", "Hello1");
    }
}
