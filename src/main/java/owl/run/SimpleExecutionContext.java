package owl.run;

import java.io.StringWriter;
import java.io.Writer;

final class SimpleExecutionContext implements PipelineExecutionContext {
  private final StringWriter writer;

  SimpleExecutionContext() {
    this.writer = new StringWriter();
  }

  @Override
  public Writer metaWriter() {
    return writer;
  }

  String getWrittenString() {
    return writer.toString();
  }
}
