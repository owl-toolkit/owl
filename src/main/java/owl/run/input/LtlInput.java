package owl.run.input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.cli.ImmutableInputSettings;
import owl.cli.ModuleSettings.InputSettings;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.PipelineExecutionException;
import owl.run.env.Environment;

public class LtlInput implements InputParser.Factory {
  public static final InputSettings settings = ImmutableInputSettings.builder()
    .key("ltl")
    .description("Parses LTL formulas")
    .inputSettingsParser(settings -> new LtlInput())
    .build();

  private static final Logger logger = Logger.getLogger(LtlInput.class.getName());

  @Override
  public InputParser createParser(InputStream input, Consumer<Object> callback,
    Environment environment) {
    Charset charset = environment.charset();
    return () -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
        String line;
        //noinspection NestedAssignment - standard pattern for reading line by line
        while ((line = reader.readLine()) != null) { // NOPMD
          if (line.isEmpty()) {
            continue;
          }

          LabelledFormula formula = LtlParser.parse(line);
          logger.log(Level.FINE, "Read formula {0} from line {1}", new Object[] {formula, line});
          callback.accept(formula);
        }
      } catch (IOException e) {
        throw PipelineExecutionException.wrap(e);
      }
    };
  }
}