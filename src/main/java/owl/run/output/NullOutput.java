package owl.run.output;

import static owl.cli.ModuleSettings.OutputSettings;

import java.io.OutputStream;
import owl.cli.ImmutableOutputSettings;
import owl.run.env.Environment;

public class NullOutput implements OutputWriter.Factory {
  public static final OutputSettings settings = ImmutableOutputSettings.builder()
    .key("null")
    .description("Discards the output - useful for performance testing")
    .outputSettingsParser(settings -> new NullOutput())
    .build();

  @Override
  public OutputWriter createWriter(OutputStream stream, Environment environment) {
    return input -> {
    };
  }
}
