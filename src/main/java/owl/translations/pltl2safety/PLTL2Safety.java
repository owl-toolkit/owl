package owl.translations.pltl2safety;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import owl.automaton.Automaton;
import owl.automaton.ImplicitNonDeterministicEdgesAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.run.Environment;

public class PLTL2Safety
  implements Function<LabelledFormula, Automaton<?, AllAcceptance>> {
  private final Environment environment;

  public PLTL2Safety(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Automaton<?, AllAcceptance> apply(LabelledFormula labelledFormula) {
    Formula formula = labelledFormula.formula().substitute(x -> {
      assert x instanceof GOperator;
      return ((GOperator) x).operand;
    });

    Set<Set<Formula>> stateSpace = getAtoms(formula);

    var factories = environment.factorySupplier().getFactories(labelledFormula.variables());
    return Views.createPowerSetAutomaton(
      new ImplicitNonDeterministicEdgesAutomaton<>(
        factories.vsFactory,
        constructInitialState(stateSpace),
        AllAcceptance.INSTANCE,
      (Set<Formula> state, BitSet letter) ->
          constructSuccessorEdges(formula, state, letter, stateSpace)
      ),
      AllAcceptance.INSTANCE,
      true
    );
  }

  private static Set<Set<Formula>> constructInitialState(Set<Set<Formula>> stateSpace) {
    Set<Set<Formula>> initialState = new HashSet<>();

    for (Set<Formula> state : stateSpace) {
      InitialStateVisitor initVisitor = new InitialStateVisitor(state);
      if (state.stream().filter(x -> !(x instanceof Literal)).allMatch(initVisitor::apply)) {
        initialState.add(state);
      }
    }
    return initialState;
  }

  private static Set<Edge<Set<Formula>>> constructSuccessorEdges(Formula formula,
    Set<Formula> state, BitSet letter, Set<Set<Formula>> stateSpace) {

    BitSet aP = formula.atomicPropositions(true);
    aP.and(letter);
    BitSet stateValuation = new BitSet();

    //Check if letter is valid
    for (Formula op : state) {
      if (op instanceof Literal) {
        Literal literal = (Literal) op;
        stateValuation.set(literal.getAtom(), !literal.isNegated());
      }
    }

    stateValuation.xor(aP);
    if (!stateValuation.isEmpty()) {
      return Set.of();
    }

    Set<Edge<Set<Formula>>> successorEdges = new HashSet<>();
    //Check for each state if successor
    for (Set<Formula> suc : stateSpace) {
      TransitionVisitor transitionVisitor = new TransitionVisitor(state, suc);
      if (suc.stream().filter(x -> !(x instanceof Literal)).allMatch(transitionVisitor::apply)) {
        successorEdges.add(Edge.of(suc));
      }
    }

    return successorEdges;
  }

  private static Set<Set<Formula>> getAtoms(Formula formula) {
    Set<Formula> cl = (new Closure(formula)).getClosure();
    Set<Formula> base = new HashSet<>();
    base.add(formula);
    Set<Set<Formula>> atoms = new HashSet<>();
    atoms.add(base);

    for (Formula psi : cl) {
      Set<Set<Formula>> negAtoms = new HashSet<>();
      for (Set<Formula> atom : atoms) {
        if (!(atom.contains(psi) || atom.contains(psi.not()))) {
          Set<Formula> negAtom = new HashSet<>(Set.copyOf(atom));
          atom.add(psi);
          negAtom.add(psi.not());
          negAtoms.add(negAtom);
        }
      }
      atoms.addAll(negAtoms);
    }

    //checks that for every (psi_1 & psi_2) in atom, psi_1 in atom and psi_2 in atom
    Predicate<Set<Formula>> atomRule1 = t ->
      t.stream().filter(Conjunction.class::isInstance)
        .allMatch(x -> (t.containsAll(x.children())));

    //checks that for every (psi_1 | psi_2) in atom, psi_1 in atom or psi_2 in atom
    Predicate<Set<Formula>> atomRule2 = t ->
      t.stream().filter(Disjunction.class::isInstance)
        .allMatch(x -> (x.children().stream().anyMatch(t::contains)));

    atoms.removeIf(atomRule1.and(atomRule2).negate());

    return atoms;
  }
}
