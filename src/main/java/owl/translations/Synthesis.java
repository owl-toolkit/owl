package owl.translations;

import java.util.List;
import owl.arena.Views;
import owl.arena.algorithms.ParityGameSolver;
import owl.run.InputReaders;
import owl.run.OutputWriters;
import owl.run.Transformers;
import owl.run.parser.CompositeModuleParser;
import owl.run.parser.ImmutableComposableModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPAModule;

public final class Synthesis {
  private Synthesis() {}

  public static void main(String... args) {
    ImmutableComposableModuleConfiguration auto =
      ImmutableComposableModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER)
      .transformer(LTL2DPAModule.INSTANCE)
      .addPostProcessors(Transformers.MINIMIZER)
      .build();

    ImmutableComposableModuleConfiguration game =
      ImmutableComposableModuleConfiguration.builder()
      .transformer(Views.SETTINGS)
      .addPostProcessors(ParityGameSolver.ZIELONKA_SOLVER)
      .writerModule(OutputWriters.STRING) // TODO: we need an AIG writer here
      .build();

    CompositeModuleParser.run(args, List.of(auto, game));
  }
}
