package owl.translations.nba2dpa;

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

public class NBA2DPAModule implements TransformerSettings {
  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.HOA)
      .transformer(new NBA2DPAModule())
      .writerModule(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer create(CommandLine settings, Environment environment)
    throws ParseException {
    return (input, context) -> new NBA2DPAFunction<>()
      .apply(AutomatonUtil.cast(input, Object.class, OmegaAcceptance.class));
  }

  @Override
  public String getKey() {
    return "nba2dpa";
  }

  @Override
  public Options getOptions() {
    return new Options();
    // .addOption("ns", "nosafety", false, "Don't calculate safety");
  }
}
