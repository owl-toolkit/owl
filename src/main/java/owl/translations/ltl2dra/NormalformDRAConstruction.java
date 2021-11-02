/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.collections.Pair;
import owl.ltl.BooleanConstant;
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
  private final DeterministicConstructionsPortfolio<GeneralizedCoBuchiAcceptance>
    sigma2GeneralizedPortfolio;

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
    this.sigma2GeneralizedPortfolio
      = new DeterministicConstructionsPortfolio<>(GeneralizedCoBuchiAcceptance.class);
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
      if (disjunct.pi2().formula().equals(BooleanConstant.TRUE)) {
        Automaton<?, ? extends GeneralizedCoBuchiAcceptance> sigma2Automaton
          = sigma2GeneralizedPortfolio.apply(disjunct.sigma2()).orElse(null);

        if (sigma2Automaton == null) {
          sigma2Automaton = DeterministicConstructionsPortfolio.coSafetySafety(disjunct.sigma2());
        }

        automata.add(OmegaAcceptanceCast.cast((Automaton) sigma2Automaton, acceptanceClass));
      } else {
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
      state.values().stream().anyMatch(NormalformDRAConstruction::isUniverse);

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
    AcceptanceOptimizations.transform(automaton);
    return OmegaAcceptanceCast.cast(automaton, acceptanceClass);
  }

  private static boolean isUniverse(Object state) {
    if (state instanceof Pair<?, ?> pair) {
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

    if (state instanceof DeterministicConstructions.BreakpointStateAcceptingRoundRobin) {
      return ((DeterministicConstructions.BreakpointStateAcceptingRoundRobin) state).all().isTrue();
    }

    if (state instanceof DeterministicConstructions.BreakpointStateRejectingRoundRobin) {
      return ((DeterministicConstructions.BreakpointStateRejectingRoundRobin) state).all().isTrue();
    }

    return false;
  }
}
