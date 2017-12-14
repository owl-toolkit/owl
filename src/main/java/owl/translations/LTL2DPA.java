package owl.translations;

import com.google.common.collect.ImmutableMap;
import owl.run.InputReaders;
import owl.run.OutputWriters;
import owl.run.Transformers;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;
import owl.translations.ltl2dpa.LTL2DPAModule;
import owl.translations.rabinizer.RabinizerModule;

public final class LTL2DPA {
  private LTL2DPA() {}

  public static void main(String... args) {
    ImmutableSingleModuleConfiguration ldba = ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER)
      .transformer(LTL2DPAModule.INSTANCE)
      .addPostProcessors(Transformers.MINIMIZER)
      .writerModule(OutputWriters.HOA)
      .build();
    ImmutableSingleModuleConfiguration rabinizerIar = ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER, Transformers.UNABBREVIATE_RW)
      .transformer(new RabinizerModule())
      .addPostProcessors(Transformers.MINIMIZER, Transformers.RABIN_DEGENERALIZATION,
        Transformers.IAR)
      .writerModule(OutputWriters.HOA)
      .build();
    SimpleModuleParser.run(args, ImmutableMap.of("ldba", ldba, "rabinizer", rabinizerIar), ldba);
  }
}
