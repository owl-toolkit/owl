package owl.translations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import owl.arena.ArenaFactory;
import owl.arena.algorithms.ParityGameSolver;
import owl.run.InputReaders;
import owl.run.OutputWriters;
import owl.run.Transformers;
import owl.run.parser.CompositeModuleParser;
import owl.run.parser.ImmutableComposableModuleConfiguration;
import owl.run.parser.SimpleModuleParser;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.rabinizer.RabinizerModule;

public final class Synthesis {
  private Synthesis() {
  }

  public static void main(String... args) {
    ImmutableComposableModuleConfiguration auto =
      ImmutableComposableModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER)
      .transformer(LTL2DPAFunction.settings)
      .addPostProcessors(Transformers.MINIMIZER)
      .build();

    
    ImmutableComposableModuleConfiguration game =
      ImmutableComposableModuleConfiguration.builder()
      .transformer(ArenaFactory.settings)
      .addPostProcessors(ParityGameSolver.zielonkaSolver)
      .writerModule(OutputWriters.HOA) // we need an AIG writer here
      .build();
    

    CompositeModuleParser.run(args, ImmutableList.of(auto, game));
  }
}
