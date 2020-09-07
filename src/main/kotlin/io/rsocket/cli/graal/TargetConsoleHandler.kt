package io.rsocket.cli.graal

import com.baulsupp.oksocial.output.ConsoleHandler
import com.baulsupp.oksocial.output.UsageException
import com.baulsupp.oksocial.output.execResult
import com.baulsupp.oksocial.output.isOSX
import com.oracle.svm.core.annotate.Substitute
import com.oracle.svm.core.annotate.TargetClass

@TargetClass(ConsoleHandler::class)
class TargetConsoleHandler {
  @Substitute
  suspend fun openLink(url: String) {
    if (isOSX) {
      val result = execResult("open", url, outputMode = ConsoleHandler.Companion.OutputMode.Hide)

      if (result != 0) {
        throw UsageException("open url failed: $url")
      }
    } else {
      System.err.println(url)
    }
  }
}
