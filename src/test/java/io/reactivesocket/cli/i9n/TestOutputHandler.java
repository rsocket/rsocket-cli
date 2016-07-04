package io.reactivesocket.cli.i9n;

import com.google.common.collect.Lists;
import io.reactivesocket.cli.OutputHandler;

import java.util.List;

public final class TestOutputHandler implements OutputHandler {
    public final List<String> stdout = Lists.newArrayList();
    public final List<String> stderr = Lists.newArrayList();

    @Override
    public void showOutput(String s) {
        stdout.add(s);
    }

    @Override
    public void info(String s) {
        stderr.add(s);
    }

    @Override
    public void error(String msg, Exception e) {
        stderr.add(msg + ": " + e.toString());
    }

    @Override
    public int hashCode() {
        return stdout.hashCode() + stderr.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TestOutputHandler)) {
            return false;
        }

        TestOutputHandler other = (TestOutputHandler) obj;

        return stderr.equals(other.stderr) && stdout.equals(other.stdout);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(4096);

        if (!stdout.isEmpty()) {
            sb.append("STDOUT:\n");
            stdout.forEach(s -> sb.append(s + "\n"));
        }

        if (!stderr.isEmpty()) {
            sb.append("STDERR:\n");
            stderr.forEach(s -> sb.append(s + "\n"));
        }

        return sb.toString();
    }
}
