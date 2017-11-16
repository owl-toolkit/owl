package owl.translations;

import com.google.common.collect.ImmutableMap;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.cli.parser.ImmutableSingleModuleConfiguration;
import owl.cli.parser.SimpleModuleParser;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.rewriter.RewriterTransformer;
import owl.ltl.visitors.DefaultConverter;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.input.LtlInput;
import owl.run.meta.ToHoa;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.rabinizer.RabinizerModule;

public final class LTL2DPA {
  private LTL2DPA() {}

  public static void main(String... args) {
    ImmutableSingleModuleConfiguration ldba = ImmutableSingleModuleConfiguration.builder()
      .inputParser(new LtlInput())
      .addPreProcessors(new RewriterTransformer(RewriterEnum.MODAL_ITERATIVE))
      .transformer(LTL2DPAFunction.settings)
      .addPostProcessors(environment -> new ImplicitMinimizeTransformer())
      .outputWriter(new ToHoa())
      .build();
    ImmutableSingleModuleConfiguration rabinizerIar = ImmutableSingleModuleConfiguration.builder()
      .inputParser(new LtlInput())
      .addPreProcessors(new RewriterTransformer(RewriterEnum.MODAL_ITERATIVE))
      .addPreProcessors(env -> {
        UnabbreviateVisitor visitor = new UnabbreviateVisitor(WOperator.class, ROperator.class);
        return DefaultConverter.asTransformer(visitor);
      })
      .transformer(new RabinizerModule())
      .addPostProcessors(environment -> new ImplicitMinimizeTransformer())
      .addPostProcessors(environment -> new RabinDegeneralization())
      .addPostProcessors(IARBuilder.FACTORY)
      .outputWriter(new ToHoa())
      .build();
    SimpleModuleParser.run(args, ImmutableMap.of("ldba", ldba, "rabinizer", rabinizerIar), ldba);
  }
}
