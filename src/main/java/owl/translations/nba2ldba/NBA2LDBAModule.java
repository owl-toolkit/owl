package owl.translations.nba2ldba;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.cli.parser.ImmutableSingleModuleConfiguration;
import owl.cli.parser.SimpleModuleParser;
import owl.run.input.HoaInput;
import owl.run.meta.ToHoa;
import owl.run.transformer.Transformer.Factory;
import owl.translations.Optimisation;

public class NBA2LDBAModule implements TransformerSettings {
  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .inputParser(new HoaInput())
      .transformer(new NBA2LDBAModule())
      .outputWriter(new ToHoa())
      .build());
  }

  @Override
  public Factory parseTransformerSettings(CommandLine settings) throws ParseException {
    return environment -> {
      NBA2LDBAFunction<Object> function =
        new NBA2LDBAFunction<>(EnumSet.of(Optimisation.REMOVE_EPSILON_TRANSITIONS));
      return (input, context) -> function.apply(
        AutomatonUtil.cast(input, Object.class, OmegaAcceptance.class));
    };
  }

  @Override
  public String getKey() {
    return "nba2ldba";
  }

  @Override
  public Options getOptions() {
    return new Options();
  }
}
