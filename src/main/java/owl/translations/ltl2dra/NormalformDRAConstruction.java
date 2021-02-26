package owl.translations.ltl2dra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.HashMapAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.collections.Pair;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierFactory;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.mastertheorem.Normalisation;
import owl.util.ParallelEvaluation;

public final class NormalformDRAConstruction<R extends GeneralizedRabinAcceptance>
  implements Function<LabelledFormula, Automaton<?, ? extends R>> {

  private static final Normalisation NORMALISATION
    = Normalisation.of(false, false, false);

  private static final Normalisation DUAL_NORMALISATION
    = Normalisation.of(true, false, false);

  private final Class<R> acceptanceClass;
  private final boolean dualConstruction;

  private final DeterministicConstructionsPortfolio<R> singleRabinPortfolio;

  private final DeterministicConstructionsPortfolio<? extends GeneralizedBuchiAcceptance>
    pi2Portfolio;
  private final DeterministicConstructionsPortfolio<CoBuchiAcceptance>
    sigma2Portfolio;

  private NormalformDRAConstruction(Class<R> acceptanceClass, boolean dualConstruction) {
    this.acceptanceClass = acceptanceClass;
    this.dualConstruction = dualConstruction;

    var buchiAcceptance = acceptanceClass.equals(GeneralizedRabinAcceptance.class)
      ? GeneralizedBuchiAcceptance.class
      : BuchiAcceptance.class;

    this.pi2Portfolio
      = new DeterministicConstructionsPortfolio<>(buchiAcceptance);
    this.sigma2Portfolio
      = new DeterministicConstructionsPortfolio<>(CoBuchiAcceptance.class);
    this.singleRabinPortfolio
      = new DeterministicConstructionsPortfolio<>(acceptanceClass);
  }

  public static <R extends GeneralizedRabinAcceptance> NormalformDRAConstruction<R>
    of(Class<R> acceptanceClass, boolean dualConstruction) {
    return new NormalformDRAConstruction<>(acceptanceClass, dualConstruction);
  }

  @Override
  public Automaton<?, ? extends R> apply(LabelledFormula formula) {
    // Ensure that the input formula is in negation normal form.
    var nnfFormula = formula.nnf();

    if (SyntacticFragments.DELTA_2.contains(nnfFormula) || !dualConstruction) {
      return apply(nnfFormula, false);
    }

    Supplier<Optional<Automaton<?, ? extends R>>> supplier1 = () ->
      Optional.of(apply(nnfFormula, false));
    Supplier<Optional<Automaton<?, ? extends R>>> supplier2 = () ->
      Optional.of(apply(nnfFormula, true));

    return ParallelEvaluation.takeSmallestWildcardStateType(
      ParallelEvaluation.evaluate(List.of(supplier1, supplier2)));
  }

  private Optional<Automaton<?, ? extends R>> portfolio(LabelledFormula formula) {
    var result1 = pi2Portfolio.apply(formula);

    if (result1.isPresent()) {
      return result1.map(x -> OmegaAcceptanceCast.cast(x, acceptanceClass));
    }

    var result2 = sigma2Portfolio.apply(formula);
    return result2.map(x -> OmegaAcceptanceCast.cast(x, acceptanceClass));
  }

  public Automaton<?, ? extends R> apply(
    LabelledFormula labelledFormula, boolean dualConstruction) {

    // We apply the normalisation only if the formula is outside of Δ₂.
    LabelledFormula normalForm = SyntacticFragments.DELTA_2.contains(labelledFormula)
      ? labelledFormula
      : (dualConstruction ? DUAL_NORMALISATION : NORMALISATION).apply(labelledFormula);

    // Check if the portfolio can directly deal with the normal form.
    Optional<Automaton<?, ? extends R>> portfolioAutomaton = portfolio(normalForm);

    if (portfolioAutomaton.isPresent()) {
      return portfolioAutomaton.get();
    }

    // Split into disjunctive normal form.
    Set<Set<Formula>> dnf = NormalForms.toDnf(normalForm.formula());

    List<Automaton<?, ? extends R>> automata = new ArrayList<>();

    for (Set<Formula> clause : dnf) {
      Set<Formula> delta1 = new HashSet<>();
      Set<Formula> sigma2 = new HashSet<>();
      Set<Formula> pi2 = new HashSet<>();

      for (Formula formula : clause) {
        var formulaClass = SyntacticFragments.FormulaClass.classify(formula);

        if (formulaClass.level() <= 1) {
          delta1.add(formula);
        } else {
          assert formulaClass.level() == 2;

          if (formulaClass.type() == SyntacticFragments.Type.SIGMA) {
            sigma2.add(formula);
          } else {
            assert formulaClass.type() == SyntacticFragments.Type.PI;
            pi2.add(formula);
          }
        }
      }

      if (sigma2.isEmpty()) {
        pi2.addAll(delta1);
      } else {
        sigma2.addAll(delta1);
      }

      var sigma2Formula = LabelledFormula.of(
        SimplifierFactory.apply(Conjunction.of(sigma2), SimplifierFactory.Mode.SYNTACTIC_FIXPOINT),
        normalForm.atomicPropositions());

      var pi2Formula = LabelledFormula.of(
        SimplifierFactory.apply(Conjunction.of(pi2), SimplifierFactory.Mode.SYNTACTIC_FIXPOINT),
        normalForm.atomicPropositions());

      if (pi2.isEmpty()) {
        Automaton<?, ? extends R> singleRabinAutomaton = singleRabinPortfolio.apply(sigma2Formula)
          .orElseGet(() -> OmegaAcceptanceCast.cast(
            DeterministicConstructionsPortfolio.coSafetySafety(sigma2Formula),
            acceptanceClass));
        automata.add(singleRabinAutomaton);
        continue;
      }

      if (sigma2.isEmpty()) {
        var singleRabinAutomaton = singleRabinPortfolio.apply(pi2Formula).orElseGet(
          () -> OmegaAcceptanceCast.cast(
            DeterministicConstructionsPortfolio.safetyCoSafety(pi2Formula),
            acceptanceClass));
        automata.add(singleRabinAutomaton);
        continue;
      }

      var sigma2Automaton = sigma2Portfolio.apply(sigma2Formula).orElseGet(
        () -> OmegaAcceptanceCast.cast(
          DeterministicConstructionsPortfolio.coSafetySafety(sigma2Formula),
          CoBuchiAcceptance.class));
      Automaton<?, ?> pi2Automaton = pi2Portfolio.apply(pi2Formula).orElseGet(
        () -> (Automaton)
          DeterministicConstructionsPortfolio.safetyCoSafety(pi2Formula));
      var intersectionAutomaton
        = BooleanOperations.intersection(sigma2Automaton, pi2Automaton);
      automata.add(OmegaAcceptanceCast.cast((Automaton) intersectionAutomaton, acceptanceClass));
    }

    Automaton<Map<Integer, ?>, ?> unionAutomaton
      = BooleanOperations.deterministicUnion((List) automata);
    var automaton
      = HashMapAutomaton.copyOf(OmegaAcceptanceCast.cast(unionAutomaton, acceptanceClass));

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
