package io.rsocket.cli.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.rsocket.cli.UtilKt;

@TargetClass(UtilKt.class)
public final class TargetUtilKt {
  @Substitute
  public static void configureLogging(boolean debug) {
  }
}
