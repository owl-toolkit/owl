package owl.translations;

import owl.game.algorithms.ZielonkaSolver;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPACliParser;
import owl.translations.tlsf2arena.TLSF2ArenaFunction;

public final class LTL2Strategy {
  private LTL2Strategy() {
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl-synth")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(LTL2DPACliParser.INSTANCE)
      .addTransformer(TLSF2ArenaFunction.settings)
      .addTransformer(ZielonkaSolver.ZIELONKA_SOLVER)
      .writer(OutputWriters.TO_STRING)
      .build());
  }
}
