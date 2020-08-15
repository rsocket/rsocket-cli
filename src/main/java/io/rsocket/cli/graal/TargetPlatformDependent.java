package io.rsocket.cli.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.netty.util.internal.PlatformDependent;

import static reactor.util.Loggers.useJdkLoggers;

@TargetClass(PlatformDependent.class)
public final class TargetPlatformDependent {
  @Substitute
  public static boolean isAndroid() {
    return false;
  }

  @Substitute
  public static boolean hasUnsafe() {
    return false;
  }
}
