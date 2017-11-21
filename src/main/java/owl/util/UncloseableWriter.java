package owl.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class UncloseableWriter extends Writer {
  private final Writer delegate;

  private UncloseableWriter(Writer delegate) {
    this.delegate = delegate;
  }

  public static Writer syserr() {
    return new UncloseableWriter(new OutputStreamWriter(System.err));
  }

  public static Writer sysout() {
    return new UncloseableWriter(new OutputStreamWriter(System.out));
  }

  @Override
  public void close() throws IOException {
    // Just flush.
    delegate.flush();
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);
  }

  @Override
  public void write(char[] cbuf, int off, int len) throws IOException {
    delegate.write(cbuf, off, len);
  }
}
