package owl.translations.rabinizer;

import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class RabinizerMain {
  private RabinizerMain() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2dgra")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.SIMPLIFIER)
      .addTransformer(RabinizerCliParser.INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
