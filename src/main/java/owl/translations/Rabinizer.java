package owl.translations;

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.minimizations.GeneralizedRabinMinimizations;
import owl.automaton.minimizations.GenericMinimizations;
import owl.automaton.minimizations.MinimizationUtil;
import owl.automaton.output.HoaPrintable;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.Formula;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.ltl.visitors.Visitor;
import owl.translations.rabinizer.ImmutableRabinizerConfiguration;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerState;

public final class Rabinizer extends AbstractLtlCommandLineTool {
  private static final Visitor<Formula> expandVisitor =
    new UnabbreviateVisitor(ROperator.class, WOperator.class);
  private static final Logger logger = Logger.getLogger(Rabinizer.class.getName());

  public static void main(String... argsArray) {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new Rabinizer().execute(args);
  }

  @Override
  protected Function<Formula, ? extends HoaPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {

    return f -> {
      Formula formula = f;
      // TODO Not sure which rewrites should happen here - document which are essential and why
      formula = formula.accept(expandVisitor);
      formula = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
      formula = RewriterFactory.apply(RewriterEnum.PUSHDOWN_X, formula);
      Factories factories = Registry.getFactories(formula);
      logger.log(Level.FINE, "Got formula {0}, rewritten to {1}", new Object[] {f, formula});
      MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> automaton =
        RabinizerBuilder.rabinize(formula, ImmutableRabinizerConfiguration.builder()
          .removeFormulaRepresentative(false)
          .factories(factories)
          .build());
      MinimizationUtil.applyMinimization(automaton, ImmutableList.of(
        GeneralizedRabinMinimizations.minimizeOverlap(),
        GeneralizedRabinMinimizations.minimizeMergePairs(),
        GeneralizedRabinMinimizations.minimizeComplementaryInf(),
        GenericMinimizations.removeTransientAcceptance(),
        GeneralizedRabinMinimizations.minimizeGloballyIrrelevant(),
        GeneralizedRabinMinimizations.minimizeEdgeImplications(),
        GeneralizedRabinMinimizations.minimizeSccIrrelevant(),
        GeneralizedRabinMinimizations.minimizeTrivial(),
        GeneralizedRabinMinimizations.minimizePairImplications(),
        GeneralizedRabinMinimizations.minimizeMergePairs(),
        GeneralizedRabinMinimizations.minimizeComplementaryInf(),
        GeneralizedRabinMinimizations.minimizePairImplications(),
        GeneralizedRabinMinimizations.minimizeEdgeImplications(),
        GeneralizedRabinMinimizations.minimizeSccIrrelevant(),
        GeneralizedRabinMinimizations.minimizeGloballyIrrelevant()
      ));
      return automaton;
      // return RabinUtil.fromGeneralizedRabin(automaton);
      // return IARFactory.<DegeneralizedRabinState<RabinizerState>>translator()
      //   .apply(RabinUtil.fromGeneralizedRabin(automaton));
    };
  }
}
