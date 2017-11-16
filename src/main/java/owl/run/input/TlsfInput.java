package owl.run.input;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import owl.cli.ImmutableInputSettings;
import owl.cli.ModuleSettings.InputSettings;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.run.PipelineExecutionException;
import owl.run.env.Environment;

public class TlsfInput implements InputParser.Factory {
  public static final InputSettings settings = ImmutableInputSettings.builder()
    .key("tlsf")
    .description("Parses a single TLSF instance and converts it to an LTL formula")
    .inputSettingsParser(settings -> new TlsfInput())
    .build();

  @Override
  public InputParser createParser(InputStream input, Consumer<Object> callback,
    Environment environment) {
    return () -> {
      try {
        Tlsf tlsf = TlsfParser.parse(input, environment.charset());
        LabelledFormula formula = tlsf.toFormula();
        callback.accept(formula);
      } catch (IOException e) {
        throw PipelineExecutionException.wrap(e);
      }
    };
  }
}
