package owl.translations.rabinizer;

import owl.run.InputReaders;
import owl.run.OutputWriters;
import owl.run.Transformers;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;

public final class RabinizerDegeneralizeMain {
  private RabinizerDegeneralizeMain() {
  }

  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(RabinizerMain.SIMPLIFIER, Transformers.UNABBREVIATE_RW)
      .transformer(new RabinizerModule())
      .addPostProcessors(Transformers.MINIMIZER, Transformers.RABIN_DEGENERALIZATION)
      .writerModule(OutputWriters.HOA)
      .build());
  }
}
