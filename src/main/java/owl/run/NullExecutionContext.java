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
  public void printMeta(String line) {}

  private static final class NullWriter extends Writer {
    @Override
    public void write(char[] cbuf, int off, int len) {}

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}

