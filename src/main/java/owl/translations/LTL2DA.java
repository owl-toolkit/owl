package owl.translations;

import java.util.EnumSet;
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrintable;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class LTL2DA {
  public static final TransformerParser CLI_PARSER = ImmutableTransformerParser.builder()
    .key("ltl2da")
    .parser(settings -> environment ->
      Transformers.instanceFromFunction(LabelledFormula.class,
        formula -> translate(environment, formula)))
    .build();

  private LTL2DA() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2da")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.SIMPLIFY_MODAL_ITER)
      .addTransformer(CLI_PARSER)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build());
  }

  private static HoaPrintable translate(Environment env, LabelledFormula formula) {
    LTL2DPAFunction ltl2Dpa = new LTL2DPAFunction(env,
      LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG);
    LimitDeterministicAutomaton<?, ?, ?, ?> ldba = LTL2LDBAFunction
      .createGeneralizedBreakpointLDBABuilder(env,
        EnumSet.allOf(Configuration.class)).apply(formula);
    Automaton<?, ?> automaton = ltl2Dpa.apply(formula);

    if (ldba.isDeterministic()
      && ldba.getAcceptingComponent().getStates().size() <= automaton.getStates().size()) {
      automaton = ldba.getAcceptingComponent();
    }

    return automaton;
  }
}
