package io.rsocket.cli.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import static reactor.util.Loggers.useJdkLoggers;

@TargetClass(reactor.util.Loggers.class)
public final class TargetLoggers {
  @Substitute
  public static void resetLoggerFactory() {
    useJdkLoggers();
  }
}
