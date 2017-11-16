package owl.util;

import java.io.IOException;
import java.io.OutputStream;

public class CloseGuardOutputStream extends OutputStream {
  private final OutputStream delegate;

  public CloseGuardOutputStream(OutputStream delegate) {
    this.delegate = delegate;
  }

  public static OutputStream syserr() {
    return new CloseGuardOutputStream(System.err);
  }

  public static OutputStream sysout() {
    return new CloseGuardOutputStream(System.out);
  }

  @Override
  public void close() throws IOException {
    // Ignore close
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    delegate.write(b, off, len);
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    delegate.write(b);
  }
}
