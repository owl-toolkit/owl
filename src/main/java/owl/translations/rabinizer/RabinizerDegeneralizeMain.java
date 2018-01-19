package owl.translations.rabinizer;

import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class RabinizerDegeneralizeMain {
  private RabinizerDegeneralizeMain() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args,  PartialModuleConfiguration.builder("ltl2dra")
      .reader(InputReaders.LTL)
      .addTransformer(RabinizerMain.SIMPLIFIER, Transformers.UNABBREVIATE_RW)
      .addTransformer(RabinizerCliParser.INSTANCE)
      .addTransformer(Transformers.MINIMIZER, Transformers.RABIN_DEGENERALIZATION)
      .writer(OutputWriters.HOA)
      .build());
  }
}
