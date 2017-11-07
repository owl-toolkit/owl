package owl.run.input;

import java.io.InputStream;
import java.util.function.Consumer;
import jhoafparser.consumer.HOAConsumerFactory;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.HOAFParserSettings;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.transformations.ToTransitionAcceptance;
import owl.automaton.AutomatonReader;
import owl.cli.ImmutableInputSettings;
import owl.cli.ModuleSettings.InputSettings;
import owl.run.PipelineExecutionException;
import owl.run.env.Environment;

public class HoaInput implements InputParser.Factory {
  public static final InputSettings settings = ImmutableInputSettings.builder()
    .key("hoa")
    .description("Parses automata given in HOA format")
    .inputSettingsParser(settings -> new HoaInput())
    .build();

  @Override
  public InputParser createParser(InputStream input, Consumer<Object> callback,
    Environment environment) {
    HOAFParserSettings settings = new HOAFParserSettings();
    settings.setFlagValidate(false);

    return new HoaParser(() -> new HOAIntermediateCheckValidity(new ToTransitionAcceptance(
      AutomatonReader.getConsumer(callback, environment.factorySupplier()))), input, settings);
  }

  private static final class HoaParser implements InputParser {
    private final HOAConsumerFactory factory;
    private final InputStream input;
    private final HOAFParserSettings settings;

    public HoaParser(HOAConsumerFactory factory, InputStream input, HOAFParserSettings settings) {
      this.factory = factory;
      this.input = input;
      this.settings = settings;
    }

    @Override
    public void run() {
      try {
        HOAFParser.parseHOA(input, factory, settings);
      } catch (ParseException e) {
        throw PipelineExecutionException.wrap(e);
      }
    }
  }
}
