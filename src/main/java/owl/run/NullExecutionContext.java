package owl.run;

import java.io.Writer;

public class NullExecutionContext implements PipelineExecutionContext {
  @SuppressWarnings({"resource", "IOResourceOpenedButNotSafelyClosed"})
  private static final NullWriter writer = new NullWriter();

  @Override
  public Writer getMetaWriter() {
    return writer;
  }

  @Override
  public void printMeta(String line) {
    // Nop.
  }

  private static final class NullWriter extends Writer {
    @Override
    public void write(char[] cbuf, int off, int len) {
      // Nop.
    }

    @Override
    public void flush() {
      // Nop.
    }

    @Override
    public void close() {
      // Nop.
    }
  }
}

