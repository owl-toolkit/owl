package owl.translations.rabinizer;

import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory;
import owl.run.InputReaders;
import owl.run.OutputWriters;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;

public final class RabinizerMain {
  private RabinizerMain() {}

  static final Transformer SIMPLIFIER = Transformers.fromFunction(LabelledFormula.class,
    x -> RewriterFactory.apply(x, RewriterFactory.RewriterEnum.PULLUP_X,
      RewriterFactory.RewriterEnum.MODAL_ITERATIVE));

  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(SIMPLIFIER, Transformers.UNABBREVIATE_RW)
      .transformer(new RabinizerModule())
      .addPostProcessors(Transformers.MINIMIZER)
      .writerModule(OutputWriters.HOA)
      .build());
  }
}
