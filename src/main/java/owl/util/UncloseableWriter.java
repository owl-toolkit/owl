package owl.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * A wrapper for writers which only forwards a {@link Writer#flush()} upon {@link #close()}. This
 * is useful in combination with, e.g., {@link System#out}, since upon calling close on that stream,
 * nothing can be written to console anymore.
 *
 * <p><strong>Warning:</strong> Code using these writers should still use try-with-resource guards
 * or similar, as otherwise the output may not get flushed.</p>
 */
@SuppressWarnings({"resource", "IOResourceOpenedButNotSafelyClosed"})
public final class UncloseableWriter extends Writer {
  public static final Writer syserr = new UncloseableWriter(new OutputStreamWriter(System.err,
    StandardCharsets.UTF_8));
  public static final Writer sysout = new UncloseableWriter(new OutputStreamWriter(System.out,
    StandardCharsets.UTF_8));

  private final Writer delegate;

  private UncloseableWriter(Writer delegate) {
    this.delegate = delegate;
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
