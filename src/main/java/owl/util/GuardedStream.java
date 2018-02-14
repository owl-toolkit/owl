package owl.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Wrappers for streams which only forward a {@link OutputStream#flush()} upon
 * {@link OutputStream#close()}. This is useful in combination with, e.g., {@link System#out},
 * since upon calling close on that stream, nothing can be written to console anymore.
 *
 * <p><strong>Warning:</strong> Code using these writers should still use try-with-resource guards
 * or similar, as otherwise the output may not get flushed.</p>
 */
@SuppressWarnings({"resource", "IOResourceOpenedButNotSafelyClosed"})
public final class GuardedStream {
  public static final Writer syserr = guard(new OutputStreamWriter(System.err,
    StandardCharsets.UTF_8));
  public static final OutputStream sysout = guard(System.out);

  private GuardedStream() {}

  public static Writer guard(Writer writer) {
    return new SafeWriter(writer);
  }

  public static OutputStream guard(OutputStream stream) {
    return new SafeOutputStream(stream);
  }

  private static final class SafeWriter extends Writer {
    private final Writer delegate;

    SafeWriter(Writer delegate) {
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

  private static final class SafeOutputStream extends OutputStream {
    private final OutputStream delegate;

    SafeOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
      delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() throws IOException {
      delegate.flush();
    }
  }
}
