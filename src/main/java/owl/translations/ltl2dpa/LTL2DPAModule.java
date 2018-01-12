package owl.translations.ltl2dpa;

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import java.util.EnumSet;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.ltl.LabelledFormula;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.env.Environment;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2ldba.LTL2LDBAModule;

public final class LTL2DPAModule implements TransformerSettings {
  public static final LTL2DPAModule INSTANCE = new LTL2DPAModule();

  private static final List<String> COMPLEMENT = List.of("c", "complement",
    "Compute the automaton also for the negation and return the smaller.");

  private LTL2DPAModule() {}

  @Override
  public Transformer create(CommandLine settings, Environment environment) {
    EnumSet<Configuration> configuration;

    if (settings.hasOption(LTL2LDBAModule.SIMPLE.get(0))) {
      configuration = EnumSet.noneOf(Configuration.class);
    } else {
      configuration = EnumSet.of(EXISTS_SAFETY_CORE, OPTIMISED_STATE_STRUCTURE,
        OPTIMISE_INITIAL_STATE);
    }

    if (settings.hasOption(COMPLEMENT.get(0))) {
      configuration.add(COMPLEMENT_CONSTRUCTION);
    }

    if (settings.hasOption(LTL2LDBAModule.GUESS_F.get(0))) {
      configuration.add(GUESS_F);
    }

    return Transformers.fromFunction(LabelledFormula.class,
      new LTL2DPAFunction(environment, configuration));
  }

  @Override
  public String getKey() {
    return "ltl2dpa";
  }

  @Override
  public Options getOptions() {
    Options options = new Options();

    for (List<String> option : List.of(COMPLEMENT, LTL2LDBAModule.GUESS_F, LTL2LDBAModule.SIMPLE)) {
      options.addOption(option.get(0), option.get(1), false, option.get(2));
    }

    return options;
  }
}
