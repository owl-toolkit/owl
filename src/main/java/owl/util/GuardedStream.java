/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
  @SuppressWarnings("SpellCheckingInspection")
  public static final Writer syserr = guard(new OutputStreamWriter(System.err,
    StandardCharsets.UTF_8));
  @SuppressWarnings("SpellCheckingInspection")
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
    public void write(char[] buffer, int off, int len) throws IOException {
      delegate.write(buffer, off, len);
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
