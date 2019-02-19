package owl.translations.pltl2safety;

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
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.run.Environment;

public class PLTL2Safety
  implements Function<LabelledFormula, Automaton<?, AllAcceptance>> {
  private final Environment environment;

  public PLTL2Safety(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Automaton<Set<Set<Formula>>, AllAcceptance> apply(LabelledFormula labelledFormula) {
    //This shouldn't be necessary, but for safe measures it's here
    assert labelledFormula.formula() instanceof GOperator;
    Formula formula = ((GOperator) labelledFormula.formula()).operand;

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

    //Check if state satisfies initial condition
    Predicate<Set<Formula>> initialCondition = t -> {
      InitialStateVisitor initialStateVisitor = new InitialStateVisitor(t);
      return (t.stream().filter(x -> !(x instanceof Literal))
        .allMatch(initialStateVisitor::apply));
    };

    return stateSpace.stream().filter(initialCondition).collect(Collectors.toSet());
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

    //Check if successor satisfies the transition relation
    Set<Formula> xOperands = state.stream().filter(XOperator.class::isInstance)
      .map(x -> ((XOperator) x).operand).collect(Collectors.toSet());
    Predicate<Set<Formula>> transitionRelation = t -> {
      TransitionVisitor transitionVisitor = new TransitionVisitor(state, t);
      return Stream.concat(t.stream().filter(x -> !(x instanceof Literal)), xOperands.stream())
        .allMatch(transitionVisitor::apply);
    };

    //Determine Set of successor edges
    return stateSpace.stream().filter(transitionRelation).map(Edge::of)
      .collect(Collectors.toSet());
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
