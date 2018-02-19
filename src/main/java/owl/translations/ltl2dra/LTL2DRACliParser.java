package owl.translations.ltl2dra;

import static owl.run.modules.OwlModuleParser.TransformerParser;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.ltl.LabelledFormula;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.translations.ltl2dra.LTL2DRAFunction.Configuration;
import owl.translations.ltl2ldba.LTL2LDBACliParser;

public final class LTL2DRACliParser implements TransformerParser {
  public static final LTL2DRACliParser INSTANCE = new LTL2DRACliParser();
  private static final Option DEGENERALIZE = new Option("d", "degeneralize", false,
    "Construct a Rabin automaton instead of a generalised-Rabin automaton (smaller than "
      + "general-purpose degeneralization).");

  private LTL2DRACliParser() {
  }

  @Override
  public String getKey() {
    return "ltl2dra";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to deterministic (generalized) Rabin automata, using an LDBA "
      + "construction";
  }

  @Override
  public Options getOptions() {
    return new Options().addOption(LTL2LDBACliParser.simple()).addOption(DEGENERALIZE);
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    EnumSet<LTL2DRAFunction.Configuration> configuration;

    if (commandLine.hasOption(LTL2LDBACliParser.simple().getOpt())) {
      configuration = EnumSet.noneOf(LTL2DRAFunction.Configuration.class);
    } else {
      configuration = EnumSet.of(EXISTS_SAFETY_CORE, OPTIMISED_STATE_STRUCTURE,
        OPTIMISE_INITIAL_STATE);
    }

    if (commandLine.hasOption(DEGENERALIZE.getOpt())) {
      configuration.add(Configuration.DEGENERALIZE);
    }

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      new LTL2DRAFunction(environment, configuration));
  }
}
