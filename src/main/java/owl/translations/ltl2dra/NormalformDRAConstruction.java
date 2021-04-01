package owl.translations.ltl2dra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.collections.Pair;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

public final class NormalformDRAConstruction<R extends GeneralizedRabinAcceptance>
  extends AbstractNormalformDRAConstruction
  implements Function<LabelledFormula, Automaton<?, ? extends R>> {

  private final Class<R> acceptanceClass;

  private final DeterministicConstructionsPortfolio<? extends GeneralizedBuchiAcceptance>
    pi2Portfolio;
  private final DeterministicConstructionsPortfolio<CoBuchiAcceptance>
    sigma2Portfolio;

  private NormalformDRAConstruction(Class<R> acceptanceClass, boolean useDualConstruction) {
    super(useDualConstruction);

    this.acceptanceClass = acceptanceClass;

    var buchiAcceptance = acceptanceClass.equals(GeneralizedRabinAcceptance.class)
      ? GeneralizedBuchiAcceptance.class
      : BuchiAcceptance.class;

    this.pi2Portfolio
      = new DeterministicConstructionsPortfolio<>(buchiAcceptance);
    this.sigma2Portfolio
      = new DeterministicConstructionsPortfolio<>(CoBuchiAcceptance.class);
  }

  public static <R extends GeneralizedRabinAcceptance> NormalformDRAConstruction<R>
    of(Class<R> acceptanceClass, boolean dualConstruction) {
    return new NormalformDRAConstruction<>(acceptanceClass, dualConstruction);
  }

  @Override
  public Automaton<?, ? extends R> apply(LabelledFormula formula) {
    // Ensure that the input formula is in negation normal form.
    var nnfFormula = formula.nnf();

    List<Automaton<Object, ? extends R>> automata = new ArrayList<>();

    for (Sigma2Pi2Pair disjunct : group(nnfFormula)) {
      Automaton<?, ? extends CoBuchiAcceptance> sigma2Automaton
        = sigma2Portfolio.apply(disjunct.sigma2()).orElse(null);

      if (sigma2Automaton == null) {
        sigma2Automaton = DeterministicConstructionsPortfolio.coSafetySafety(disjunct.sigma2());
      }

      Automaton<?, ? extends GeneralizedBuchiAcceptance> pi2Automaton
        = pi2Portfolio.apply(disjunct.pi2()).orElse(null);

      if (pi2Automaton == null) {
        pi2Automaton = DeterministicConstructionsPortfolio.safetyCoSafety(disjunct.pi2());
      }

      automata.add(OmegaAcceptanceCast.cast(
        (Automaton) BooleanOperations.intersection(sigma2Automaton, pi2Automaton),
        acceptanceClass));
    }

    if (automata.isEmpty()) {
      return OmegaAcceptanceCast.cast(
        EmptyAutomaton.of(nnfFormula.atomicPropositions(), AllAcceptance.INSTANCE),
        acceptanceClass);
    }

    var automaton = HashMapAutomaton.copyOf(
      OmegaAcceptanceCast.cast(BooleanOperations.deterministicUnion(automata), acceptanceClass));

    // Collapse accepting sinks.
    Predicate<Map<Integer, ?>> isAcceptingSink = state ->
      state.values().stream().anyMatch((Predicate<Object>) NormalformDRAConstruction::isUniverse);

    var acceptingSink = automaton.states().stream().filter(isAcceptingSink).findAny();

    if (acceptingSink.isPresent()) {
      automaton.updateEdges((state, oldEdge) -> {
        if (isAcceptingSink.test(oldEdge.successor())) {
          return oldEdge.withSuccessor(acceptingSink.get());
        }

        return oldEdge;
      });

      automaton.trim();
    }

    AcceptanceOptimizations.removeDeadStates(automaton);
    AcceptanceOptimizations.optimize(automaton);
    return OmegaAcceptanceCast.cast(automaton, acceptanceClass);
  }

  private static boolean isUniverse(Object state) {
    if (state instanceof Pair) {
      Pair<?, ?> pair = (Pair<?, ?>) state;
      return isUniverse(pair.fst()) && isUniverse(pair.snd());
    }

    if (state instanceof EquivalenceClass) {
      return ((EquivalenceClass) state).isTrue();
    }

    if (state instanceof DeterministicConstructions.BreakpointStateAccepting) {
      return ((DeterministicConstructions.BreakpointStateAccepting) state).all().isTrue();
    }

    if (state instanceof DeterministicConstructions.BreakpointStateRejecting) {
      return ((DeterministicConstructions.BreakpointStateRejecting) state).all().isTrue();
    }

    return false;
  }
}
