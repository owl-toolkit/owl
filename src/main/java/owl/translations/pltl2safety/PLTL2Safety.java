package owl.translations.pltl2safety;

import com.google.common.collect.Sets;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import owl.automaton.Automaton;
import owl.automaton.ImplicitNonDeterministicEdgesAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.ltl.Formula;
import owl.ltl.Formula.ModalOperator;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.run.Environment;

public class PLTL2Safety
  implements Function<LabelledFormula, Automaton<?, AllAcceptance>> {
  private final Environment environment;

  PLTL2Safety(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Automaton<?, AllAcceptance> apply(LabelledFormula labelledFormula) {
    Formula formula = labelledFormula.formula();

    Predicate<Formula> temporalOperator = ModalOperator.class::isInstance;
    Set<TemporalOperator> principallyTemporal = formula.subformulas(temporalOperator);

    Predicate<Formula> isLiteral = Literal.class::isInstance;

    Set<TemporalOperator> literals = formula.subformulas(isLiteral).stream()
      .map((new LiteralVisitor())::apply).collect(Collectors.toSet());

    Set<TemporalOperator> parts = Stream.concat(
      principallyTemporal.stream(),
      literals.stream())
      .collect(Collectors.toSet());
    Set<Set<TemporalOperator>> stateSpace = Sets.powerSet(parts);

    var factories = environment.factorySupplier().getFactories(labelledFormula.variables());
    return Views.createPowerSetAutomaton(
      new ImplicitNonDeterministicEdgesAutomaton<>(
        factories.vsFactory,
        constructInitialState(formula, stateSpace, principallyTemporal),
        AllAcceptance.INSTANCE,
      (Set<TemporalOperator> state, BitSet letter) ->
          constructSuccessorEdges(state, letter, principallyTemporal, literals, stateSpace)
      ),
      AllAcceptance.INSTANCE,
      true
    );
  }

  private static Set<Set<TemporalOperator>> constructInitialState(Formula formula,
    Set<Set<TemporalOperator>> stateSpace, Set<TemporalOperator> principallyTemporal) {
    Set<Set<TemporalOperator>> initialState = new HashSet<>();

    for (Set<TemporalOperator> state : stateSpace) {
      InitialStateVisitor initVisitor = new InitialStateVisitor(state);
      boolean isInitial = initVisitor.apply(formula)
        && principallyTemporal.stream().allMatch(initVisitor::apply);
      if (isInitial) {
        initialState.add(state);
      }
    }
    return initialState;
  }

  private static Set<Edge<Set<TemporalOperator>>> constructSuccessorEdges(
    Set<TemporalOperator> state, BitSet letter, Set<TemporalOperator> principallyTemporal,
    Set<TemporalOperator> literals, Set<Set<TemporalOperator>> stateSpace) {
    Set<Edge<Set<TemporalOperator>>> successorEdges = new HashSet<>();

    //Check if letter is valid
    for (TemporalOperator op : literals) {
      if (op instanceof Literal) {
        Literal literal = (Literal) op;
        if (state.contains(literal) != letter.get(literal.getAtom())) {
          return successorEdges;
        }
      }
    }

    //Check for each state if successor
    for (Set<TemporalOperator> suc : stateSpace) {
      TransitionVisitor transitionVisitor = new TransitionVisitor(state, suc);
      if (principallyTemporal.stream().allMatch(transitionVisitor::apply)) {
        successorEdges.add(Edge.of(suc));
      }
    }

    return successorEdges;
  }
}
