package owl.translations.ltl2dpa;

import static owl.run.modules.OwlModuleParser.TransformerParser;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
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
import owl.translations.ltl2ldba.LTL2LDBACliParser;

public final class LTL2DPACliParser implements TransformerParser {
  public static final LTL2DPACliParser INSTANCE = new LTL2DPACliParser();

  private LTL2DPACliParser() {}

  @Override
  public String getKey() {
    return "ltl2dpa";
  }

  @Override
  public Options getOptions() {
    return new Options()
      .addOption("c", "complement", false,
        "Compute the automaton also for the negation and return the smaller.")
      .addOption(LTL2LDBACliParser.guessF())
      .addOption(LTL2LDBACliParser.simple());
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    EnumSet<Configuration> configuration;

    if (commandLine.hasOption(LTL2LDBACliParser.simple().getOpt())) {
      configuration = EnumSet.noneOf(Configuration.class);
    } else {
      configuration = EnumSet.of(EXISTS_SAFETY_CORE, OPTIMISED_STATE_STRUCTURE,
        OPTIMISE_INITIAL_STATE);
    }

    if (commandLine.hasOption("complement")) {
      configuration.add(COMPLEMENT_CONSTRUCTION);
    }

    if (commandLine.hasOption(LTL2LDBACliParser.guessF().getOpt())) {
      configuration.add(GUESS_F);
    }

    configuration.add(COMPRESS_COLOURS);

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      new LTL2DPAFunction(environment, configuration));
  }
}
