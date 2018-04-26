package owl.translations;

import java.util.Map;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPACliParser;
import owl.translations.rabinizer.RabinizerCliParser;

public final class LTL2DPA {
  private LTL2DPA() {}

  public static void main(String... args) {
    PartialModuleConfiguration ldba = PartialModuleConfiguration.builder("ltl2dpa")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.SIMPLIFIER)
      .addTransformer(LTL2DPACliParser.INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build();
    PartialModuleConfiguration rabinizerIar = PartialModuleConfiguration.builder("ltl2dpa")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.SIMPLIFIER)
      .addTransformer(RabinizerCliParser.INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .addTransformer(Transformers.RABIN_DEGENERALIZATION, Transformers.RABIN_TO_PARITY)
      .writer(OutputWriters.HOA)
      .build();
    PartialConfigurationParser.run(args, Map.of("ldba", ldba, "rabinizer", rabinizerIar), ldba);
  }
}
