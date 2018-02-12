package owl.translations.rabinizer;

import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class RabinizerMain {
  static final Transformer SIMPLIFIER = Transformers.fromFunction(LabelledFormula.class,
    x -> RewriterFactory.apply(x, RewriterFactory.RewriterEnum.PULLUP_X,
      RewriterFactory.RewriterEnum.MODAL_ITERATIVE));

  private RabinizerMain() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2dgra")
      .reader(InputReaders.LTL)
      .addTransformer(SIMPLIFIER, Transformers.UNABBREVIATE_RW)
      .addTransformer(RabinizerCliParser.INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
