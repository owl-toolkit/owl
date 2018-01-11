package owl.translations.nba2ldba;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.run.InputReaders;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.OutputWriters;
import owl.run.Transformer;
import owl.run.env.Environment;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;

public class NBA2LDBAModule implements TransformerSettings {
  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.HOA)
      .transformer(new NBA2LDBAModule())
      .writerModule(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer create(CommandLine settings, Environment environment) {
    EnumSet<Configuration> configuration = EnumSet.of(Configuration.REMOVE_EPSILON_TRANSITIONS);
    NBA2LDBAFunction<Object> translation = new NBA2LDBAFunction<>(configuration);
    return (nba, context) -> translation.apply((Automaton<Object, ?>) nba);
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
