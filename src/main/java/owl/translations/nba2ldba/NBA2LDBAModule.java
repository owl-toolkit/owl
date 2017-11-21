package owl.translations.nba2ldba;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.run.InputReaders;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.OutputWriters;
import owl.run.Transformer;
import owl.run.env.Environment;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;
import owl.translations.Optimisation;

public class NBA2LDBAModule implements TransformerSettings {
  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.HOA)
      .transformer(new NBA2LDBAModule())
      .writerModule(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer create(CommandLine settings, Environment environment)
    throws ParseException {
    NBA2LDBAFunction<Object> function =
      new NBA2LDBAFunction<>(EnumSet.of(Optimisation.REMOVE_EPSILON_TRANSITIONS));
    return (input, context) -> function.apply(
      AutomatonUtil.cast(input, Object.class, OmegaAcceptance.class));
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
