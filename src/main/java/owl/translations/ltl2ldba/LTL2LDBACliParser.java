package owl.translations.ltl2ldba;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class LTL2LDBACliParser implements TransformerParser {
  public static final LTL2LDBACliParser INSTANCE = new LTL2LDBACliParser();

  private static final Option DEGENERALISE = new Option("d", "degeneralise", false,
    "Construct a Büchi automaton instead of a generalised-Büchi automaton.");
  private static final Option EPSILON = new Option("e", "epsilon", false,
    "Do not remove generated epsilon-transitions. Note: The generated output is not valid HOA, "
      + "since the format does not support epsilon transitions.");
  private static final Option NON_DETERMINISTIC = new Option("n", "non-deterministic", false,
    "Construct a non-deterministic initial component instead of a deterministic.");

  private LTL2LDBACliParser() {}

  public static Option guessF() {
    return new Option("f", "guess-f", false,
      "Guess F-operators that are infinitely often true.");
  }

  public static Option simple() {
    return new Option("s", "simple", false,
      "Use a simpler state-space construction. This disables special optimisations and redundancy "
        + "removal.");
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2ldba")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .writer(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    EnumSet<Configuration> configuration = commandLine.hasOption(simple().getOpt())
      ? EnumSet.noneOf(Configuration.class)
      : EnumSet.of(Configuration.EAGER_UNFOLD, Configuration.FORCE_JUMPS,
        Configuration.OPTIMISED_STATE_STRUCTURE, Configuration.SUPPRESS_JUMPS);

    if (commandLine.hasOption(NON_DETERMINISTIC.getOpt())) {
      configuration.add(Configuration.NON_DETERMINISTIC_INITIAL_COMPONENT);
    }

    if (commandLine.hasOption(EPSILON.getOpt())) {
      configuration.add(Configuration.EPSILON_TRANSITIONS);
    }

    Function<Environment, Function<LabelledFormula, ? extends
      LimitDeterministicAutomaton<?, ?, ?, ?>>> translatorProvider;

    if (commandLine.hasOption(DEGENERALISE.getOpt())) {
      if (commandLine.hasOption(guessF().getOpt())) {
        translatorProvider = environment ->
          LTL2LDBAFunction.createDegeneralizedBreakpointFreeLDBABuilder(environment, configuration);
      } else {
        translatorProvider = environment ->
          LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(environment, configuration);
      }
    } else {
      if (commandLine.hasOption(guessF().getOpt())) {
        translatorProvider = environment ->
          LTL2LDBAFunction.createGeneralizedBreakpointFreeLDBABuilder(environment, configuration);
      } else {
        translatorProvider = environment ->
          LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(environment, configuration);
      }
    }

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      translatorProvider.apply(environment));
  }

  @Override
  public String getKey() {
    return "ltl2ldba";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to limit-deterministic Büchi automata";
  }

  @Override
  public Options getOptions() {
    Options options = new Options();
    List.of(DEGENERALISE, EPSILON, NON_DETERMINISTIC, guessF(), simple())
      .forEach(options::addOption);
    return options;
  }
}
