package owl.run;

import java.io.StringWriter;
import java.io.Writer;

final class SimpleExecutionContext implements PipelineExecutionContext {
  private final StringWriter writer;

  public SimpleExecutionContext() {
    this.writer = new StringWriter();
  }

  @Override
  public Writer getMetaWriter() {
    return writer;
  }

  String getWrittenString() {
    return writer.toString();
  }
}
