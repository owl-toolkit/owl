package owl.translations;

import static owl.translations.LTL2DAFunction.Constructions.BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2DAFunction.Constructions.PARITY;
import static owl.translations.LTL2DAFunction.Constructions.SAFETY;

import java.util.EnumSet;
import owl.ltl.LabelledFormula;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class LTL2DAModule {
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("ltl2da")
    .description("Translates LTL to some deterministic automaton")
    .parser(settings -> environment -> {
      LTL2DAFunction function = new LTL2DAFunction(environment, false,
        EnumSet.of(SAFETY, CO_SAFETY, BUCHI, CO_BUCHI, PARITY));
      return Transformers.instanceFromFunction(LabelledFormula.class, function::apply);
    })
    .build();

  private LTL2DAModule() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2da")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(CLI)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
