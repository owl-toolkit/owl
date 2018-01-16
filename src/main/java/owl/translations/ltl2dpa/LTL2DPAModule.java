package owl.translations.ltl2dpa;

import static owl.run.modules.ModuleSettings.TransformerSettings;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.ltl.LabelledFormula;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2ldba.LTL2LDBAModule;

public final class LTL2DPAModule implements TransformerSettings {
  public static final TransformerSettings INSTANCE = new LTL2DPAModule();

  private LTL2DPAModule() {}

  @Override
  public String getKey() {
    return "ltl2dpa";
  }

  @Override
  public Options getOptions() {
    return new Options()
      .addOption("c", "complement", false,
        "Compute the automaton also for the negation and return the smaller.")
      .addOption(LTL2LDBAModule.guessF())
      .addOption(LTL2LDBAModule.simple());
  }

  @Override
  public Transformer parse(CommandLine settings) {
    EnumSet<Configuration> configuration;

    if (settings.hasOption(LTL2LDBAModule.guessF().getOpt())) {
      configuration = EnumSet.noneOf(Configuration.class);
    } else {
      configuration = EnumSet.of(EXISTS_SAFETY_CORE, OPTIMISED_STATE_STRUCTURE,
        OPTIMISE_INITIAL_STATE);
    }

    if (settings.hasOption("complement")) {
      configuration.add(COMPLEMENT_CONSTRUCTION);
    }

    if (settings.hasOption(LTL2LDBAModule.guessF().getOpt())) {
      configuration.add(GUESS_F);
    }

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      new LTL2DPAFunction(environment, configuration));
  }
}
