package owl.translations.rabinizer;

import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.cli.parser.ImmutableSingleModuleConfiguration;
import owl.cli.parser.SimpleModuleParser;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterTransformer;
import owl.ltl.visitors.DefaultConverter;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.input.LtlInput;
import owl.run.meta.ToHoa;

public final class RabinizerMain {
  private RabinizerMain() {}

  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .inputParser(new LtlInput())
      .addPreProcessors(new RewriterTransformer(
        RewriterFactory.RewriterEnum.PULLUP_X,
        RewriterFactory.RewriterEnum.MODAL_ITERATIVE))
      .addPreProcessors(env -> DefaultConverter.asTransformer(
        new UnabbreviateVisitor(ROperator.class, WOperator.class)))
      .transformer(new RabinizerModule())
      .addPostProcessors(environment -> new ImplicitMinimizeTransformer())
      .outputWriter(new ToHoa())
      .build());
  }
}
