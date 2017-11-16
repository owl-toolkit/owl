package owl.translations;

import java.util.EnumSet;
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.output.HoaPrintable;
import owl.cli.ImmutableTransformerSettings;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.cli.parser.ImmutableSingleModuleConfiguration;
import owl.cli.parser.SimpleModuleParser;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.rewriter.RewriterTransformer;
import owl.run.env.Environment;
import owl.run.input.LtlInput;
import owl.run.meta.ToHoa;
import owl.run.transformer.Transformers;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

public final class LTL2DA {
  public static final TransformerSettings settings = ImmutableTransformerSettings.builder()
    .key("ltl2da")
    .transformerSettingsParser(settings -> environment -> {
      EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);

      return Transformers.fromFunction(LabelledFormula.class,
        formula -> translate(environment, formula, optimisations));
    }).build();

  private LTL2DA() {}

  private static HoaPrintable translate(Environment env, LabelledFormula formula,
    EnumSet<Optimisation> optimisations) {
    optimisations.remove(Optimisation.COMPLETE);
    LTL2DPAFunction ltl2Dpa = new LTL2DPAFunction(env, optimisations);
    LimitDeterministicAutomaton<?, ?, ?, ?> ldba = LTL2LDBAFunction
      .createGeneralizedBreakpointLDBABuilder(env, optimisations).apply(formula);
    Automaton<?, ?> automaton = ltl2Dpa.apply(formula);

    if (ldba.isDeterministic()
      && ldba.getAcceptingComponent().stateCount() <= automaton.stateCount()) {
      automaton = ldba.getAcceptingComponent();
    }

    return automaton;
  }

  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .inputParser(new LtlInput())
      .addPreProcessors(new RewriterTransformer(RewriterEnum.MODAL_ITERATIVE))
      .transformer(settings)
      .addPostProcessors(environment -> new ImplicitMinimizeTransformer())
      .outputWriter(new ToHoa())
      .build());
  }
}
