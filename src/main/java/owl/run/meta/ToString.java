package owl.run.meta;

import java.io.OutputStream;
import owl.cli.ImmutableMetaSettings;
import owl.run.env.Environment;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

public class ToString implements Transformer.Factory, OutputWriter.Factory {
  public static final ImmutableMetaSettings<ToString> settings =
    ImmutableMetaSettings.<ToString>builder()
      .key("string")
      .description("Prints the toString() representation of all passed objects")
      .metaSettingsParser(settings -> new ToString())
      .build();

  @Override
  public Transformer createTransformer(Environment environment) {
    return MetaUtil.asTransformer(Object::toString, Object.class);
  }

  @Override
  public OutputWriter createWriter(OutputStream stream, Environment environment) {
    return MetaUtil.asOutputWriter(stream, environment, Object::toString, Object.class);
  }
}
