package owl.translations;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.run.Environment;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;

public final class SimpleTranslations {
  private SimpleTranslations() {}

  public static Automaton<DegeneralizedBreakpointState, BuchiAcceptance> buildBuchi(
    LabelledFormula formula, Environment env) {
    EnumSet<Configuration> configuration = EnumSet.of(
      Configuration.EAGER_UNFOLD,
      Configuration.SUPPRESS_JUMPS,
      Configuration.FORCE_JUMPS,
      Configuration.OPTIMISED_STATE_STRUCTURE);

    var builder = LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(env, configuration);
    var ldba = builder.apply(formula);
    var acceptingComponent = ldba.acceptingComponent();

    assert ldba.isDeterministic();

    if (acceptingComponent.initialStates().isEmpty()) {
      return AutomatonFactory.singleton(DegeneralizedBreakpointState.createSink(),
        env.factorySupplier().getValuationSetFactory(formula.variables()),
        BuchiAcceptance.INSTANCE);
    }

    return ldba.acceptingComponent();
  }

  public static Automaton<DegeneralizedBreakpointState, CoBuchiAcceptance> buildCoBuchi(
    LabelledFormula formula, Environment env) {
    return AutomatonUtil.cast(Views.complement(buildBuchi(formula.not(), env),
      DegeneralizedBreakpointState.createSink()), CoBuchiAcceptance.class);
  }

  public static Automaton<EquivalenceClass, BuchiAcceptance> buildCoSafety(LabelledFormula formula,
    Environment env) {
    Preconditions.checkArgument(SyntacticFragment.CO_SAFETY.contains(formula.formula()),
      "Formula is not from the syntactic co-safety fragment.");

    var supplier = env.factorySupplier();
    var eqFactory = supplier.getEquivalenceClassFactory(formula.variables(), false);
    var vsFactory = supplier.getValuationSetFactory(formula.variables());
    var factory = new EquivalenceClassStateFactory(eqFactory, true, false);

    BiFunction<EquivalenceClass, BitSet, Set<Edge<EquivalenceClass>>> single = (x, y) -> {
      var successor = factory.getSuccessor(x, y);

      if (successor.isFalse()) {
        return Set.of();
      }

      if (successor.isTrue()) {
        return Set.of(Edge.of(successor, 0));
      }

      return Set.of(Edge.of(successor));
    };

    Function<EquivalenceClass, Collection<LabelledEdge<EquivalenceClass>>> bulk = x -> {
      var successors = factory.getSuccessors(x, vsFactory).entrySet();
      return Collections2.transform(successors, y -> {
        var successor = y.getKey();

        if (successor.isTrue()) {
          return LabelledEdge.of(Edge.of(successor, 0), y.getValue());
        }

        return LabelledEdge.of(successor, y.getValue());
      });
    };

    return AutomatonFactory.create(factory.getInitial(formula.formula()), vsFactory, single, bulk,
      BuchiAcceptance.INSTANCE);
  }

  public static Automaton<EquivalenceClass, AllAcceptance> buildSafety(LabelledFormula formula,
    Environment env) {
    Preconditions.checkArgument(SyntacticFragment.SAFETY.contains(formula.formula()),
      "Formula is not from the syntactic safety fragment.");

    var supplier = env.factorySupplier();
    var eqFactory = supplier.getEquivalenceClassFactory(formula.variables(), false);
    var vsFactory = supplier.getValuationSetFactory(formula.variables());
    var factory = new EquivalenceClassStateFactory(eqFactory, true, false);

    BiFunction<EquivalenceClass, BitSet, Set<Edge<EquivalenceClass>>> single = (x, y) -> {
      var successor = factory.getSuccessor(x, y);
      return successor.isFalse() ? Set.of() : Set.of(Edge.of(successor));
    };

    Function<EquivalenceClass, Collection<LabelledEdge<EquivalenceClass>>> bulk = x -> {
      var successors = factory.getSuccessors(x, vsFactory).entrySet();
      return Collections2.transform(successors, y -> LabelledEdge.of(y.getKey(), y.getValue()));
    };

    return AutomatonFactory.create(factory.getInitial(formula.formula()), vsFactory, single, bulk,
      AllAcceptance.INSTANCE);
  }
}
