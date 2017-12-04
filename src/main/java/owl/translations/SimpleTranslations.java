package owl.translations;

import com.google.common.base.Preconditions;
import java.util.EnumSet;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.factories.EquivalenceClassFactory;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;
import owl.factories.jbdd.JBddSupplier;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.util.TestEnvironment;

public class SimpleTranslations {

  private static final FactorySupplier supplier = JBddSupplier.async();

  public static Automaton<DegeneralizedBreakpointState, BuchiAcceptance> buildBuchi(
    LabelledFormula formula) {
    EnumSet<Optimisation> ldbaOptimisations = EnumSet.of(
      Optimisation.DETERMINISTIC_INITIAL_COMPONENT,
      Optimisation.EAGER_UNFOLD,
      Optimisation.SUPPRESS_JUMPS,
      Optimisation.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES,
      Optimisation.FORCE_JUMPS,
      Optimisation.OPTIMISED_STATE_STRUCTURE);

    LimitDeterministicAutomaton<?, DegeneralizedBreakpointState, BuchiAcceptance, ?> ldba =
      LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(
        TestEnvironment.get(), ldbaOptimisations).apply(formula);
    assert ldba.isDeterministic();
    return ldba.getAcceptingComponent();
  }

  public static Automaton<DegeneralizedBreakpointState, CoBuchiAcceptance> buildCoBuchi(
    LabelledFormula formula) {
    return AutomatonUtil.cast(Views.complement(buildBuchi(formula),
      DegeneralizedBreakpointState.createSink()), CoBuchiAcceptance.class);
  }

  public static Automaton<EquivalenceClass, BuchiAcceptance> buildCoSafety(
    LabelledFormula labelledFormula) {
    Preconditions.checkArgument(Fragments.isCoSafety(labelledFormula.formula),
      "Formula is not from the syntactic cosafety fragment.");

    EquivalenceClassFactory eqFactory = supplier.getEquivalenceClassFactory(labelledFormula);
    ValuationSetFactory vsFactory = supplier.getValuationSetFactory(labelledFormula.variables);
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(eqFactory, true, false);

    return AutomatonFactory.createStreamingAutomaton(BuchiAcceptance.INSTANCE,
      factory.getInitial(labelledFormula.formula), vsFactory,
      (x, y) -> {
        EquivalenceClass successor = factory.getSuccessor(x, y);

        if (successor.isFalse()) {
          return null;
        } else if (successor.isTrue()) {
          return Edge.of(successor, 0);
        }

        return Edge.of(successor);
      });
  }

  public static Automaton<EquivalenceClass, AllAcceptance> buildSafety(
    LabelledFormula labelledFormula) {
    Preconditions.checkArgument(Fragments.isSafety(labelledFormula.formula),
      "Formula is not from the syntactic safety fragment.");

    EquivalenceClassFactory eqFactory = supplier.getEquivalenceClassFactory(labelledFormula);
    ValuationSetFactory vsFactory = supplier.getValuationSetFactory(labelledFormula.variables);
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(eqFactory, true, false);

    return AutomatonFactory.createStreamingAutomaton(AllAcceptance.INSTANCE,
      factory.getInitial(labelledFormula.formula), vsFactory,
      (x, y) -> {
        EquivalenceClass successor = factory.getSuccessor(x, y);
        return successor.isFalse() ? null : Edge.of(successor);
      });
  }
}
