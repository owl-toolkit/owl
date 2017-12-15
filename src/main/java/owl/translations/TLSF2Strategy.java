package owl.translations;

import owl.game.algorithms.ZielonkaSolver;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.tlsf2arena.TLSF2ArenaFunction;

public final class TLSF2Strategy {
  private TLSF2Strategy() {
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("tlsf-synth")
      .reader(InputReaders.TLSF)
      .addTransformer(TLSF2ArenaFunction.settings)
      .addTransformer(ZielonkaSolver.ZIELONKA_SOLVER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
