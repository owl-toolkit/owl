package owl.translations;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrintable;
import owl.ltl.LabelledFormula;
import owl.run.InputReaders;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.OutputWriters;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.env.Environment;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

public final class LTL2DA {
  public static final TransformerSettings settings = new TransformerSettings() {
    @Override
    public Transformer create(CommandLine settings, Environment environment) {
      return Transformers.fromFunction(LabelledFormula.class,
        formula -> translate(environment, formula, EnumSet.allOf(Optimisation.class)));
    }

    @Override
    public String getKey() {
      return "ltl2da";
    }
  };

  private LTL2DA() {
  }

  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER)
      .transformer(settings)
      .addPostProcessors(Transformers.MINIMIZER)
      .writerModule(OutputWriters.HOA)
      .build());
  }

  private static HoaPrintable translate(Environment env, LabelledFormula formula,
    EnumSet<Optimisation> optimisations) {
    optimisations.remove(Optimisation.COMPLETE);
    LTL2DPAFunction ltl2Dpa = new LTL2DPAFunction(env, optimisations);
    LimitDeterministicAutomaton<?, ?, ?, ?> ldba = LTL2LDBAFunction
      .createGeneralizedBreakpointLDBABuilder(env, optimisations).apply(formula);
    Automaton<?, ?> automaton = ltl2Dpa.apply(formula);

    if (ldba.isDeterministic()
      && ldba.getAcceptingComponent().getStates().size() <= automaton.getStates().size()) {
      automaton = ldba.getAcceptingComponent();
    }

    return automaton;
  }
}
