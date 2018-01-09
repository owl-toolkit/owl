package owl.translations.ltl2ldba;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EAGER_UNFOLD;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.FORCE_JUMPS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.NON_DETERMINISTIC_INITIAL_COMPONENT;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.LabelledFormula;
import owl.run.InputReaders;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.OutputWriters;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.env.Environment;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class LTL2LDBAModule implements TransformerSettings {
  public static final LTL2LDBAModule INSTANCE = new LTL2LDBAModule();

  private static final List<String> DEGENERALISE = List.of("d", "degeneralise",
    "Construct a Büchi automaton instead of a generalised-Büchi automaton.");

  private static final List<String> EPSILON = List.of("e", "epsilon",
    "Do not remove generated epsilon-transitions. Note: The generated output is not valid HOA, "
      + "since the format does not support epsilon transitions.");

  public static final List<String> GUESS_F = List.of("f", "guess-f",
    "Guess F-operators that are infinitely often true.");

  private static final List<String> NON_DETERMINISTIC = List.of("n", "non-deterministic",
    "Construct a non-deterministic initial component instead of a deterministic.");

  public static final List<String> SIMPLE = List.of("s", "simple",
    "Use a simpler state-space construction. This disables special optimisations and redundancy "
      + "removal.");

  private LTL2LDBAModule() {}

  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER)
      .transformer(INSTANCE)
      .writerModule(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer create(CommandLine settings, Environment environment) {
    EnumSet<Configuration> configuration = settings.hasOption(SIMPLE.get(0))
                                           ? EnumSet.noneOf(Configuration.class)
                                           : EnumSet.of(EAGER_UNFOLD, FORCE_JUMPS,
                                             OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);

    if (settings.hasOption(NON_DETERMINISTIC.get(0))) {
      configuration.add(NON_DETERMINISTIC_INITIAL_COMPONENT);
    }

    if (settings.hasOption(EPSILON.get(0))) {
      configuration.add(Configuration.EPSILON_TRANSITIONS);
    }

    Function<LabelledFormula, ? extends LimitDeterministicAutomaton> translator;

    if (settings.hasOption(DEGENERALISE.get(0))) {
      if (settings.hasOption(GUESS_F.get(0))) {
        translator = LTL2LDBAFunction
          .createDegeneralizedBreakpointFreeLDBABuilder(environment, configuration);
      } else {
        translator = LTL2LDBAFunction
          .createDegeneralizedBreakpointLDBABuilder(environment, configuration);
      }
    } else {
      if (settings.hasOption(GUESS_F.get(0))) {
        translator = LTL2LDBAFunction
          .createGeneralizedBreakpointFreeLDBABuilder(environment, configuration);
      } else {
        translator = LTL2LDBAFunction
          .createGeneralizedBreakpointLDBABuilder(environment, configuration);
      }
    }

    return Transformers.fromFunction(LabelledFormula.class, translator);
  }

  @Override
  public String getKey() {
    return "ltl2ldba";
  }

  @Override
  public Options getOptions() {
    Options options = new Options();

    for (List<String> option : List.of(DEGENERALISE, EPSILON, GUESS_F, NON_DETERMINISTIC, SIMPLE)) {
      options.addOption(option.get(0), option.get(1), false, option.get(2));
    }

    return options;
  }
}
