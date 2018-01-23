package owl.translations;

import owl.game.Views;
import owl.game.algorithms.ParityGameSolver;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPACliParser;

public final class Synthesis {
  private Synthesis() {}

  public static void main(String... args) {
    PartialModuleConfiguration builder = PartialModuleConfiguration.builder("synth")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.SIMPLIFY_MODAL_ITER)
      .addTransformer(LTL2DPACliParser.INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .addTransformer(Views.AUTOMATON_TO_GAME_CLI)
      .addTransformer(ParityGameSolver.ZIELONKA_SOLVER)
      .writer(OutputWriters.TO_STRING) // we need an AIG writer here
      .build();
    PartialConfigurationParser.run(args, builder);
  }
}
