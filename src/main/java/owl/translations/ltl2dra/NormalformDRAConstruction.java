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
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.minimization.DbwMinimization;
import owl.automaton.minimization.DcwMinimization;
import owl.collections.Pair;
import owl.ltl.LabelledFormula;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

public final class NormalformDRAConstruction
    extends AbstractNormalformDRAConstruction
    implements Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> {

  private final DeterministicConstructionsPortfolio<BuchiAcceptance>
      pi2Portfolio;
  private final DeterministicConstructionsPortfolio<CoBuchiAcceptance>
      sigma2Portfolio;
  private final boolean minimize;

  private NormalformDRAConstruction(boolean useDualConstruction, boolean minimize) {
    super(useDualConstruction);

    this.pi2Portfolio
        = new DeterministicConstructionsPortfolio<>(BuchiAcceptance.class);
    this.sigma2Portfolio
        = new DeterministicConstructionsPortfolio<>(CoBuchiAcceptance.class);
    this.minimize = minimize;
  }

  public static NormalformDRAConstruction of(boolean dualConstruction, boolean minimize) {
    return new NormalformDRAConstruction(dualConstruction, minimize);
  }

  @Override
  public Automaton<?, RabinAcceptance> apply(LabelledFormula formula) {
    // Ensure that the input formula is in negation normal form.
    var nnfFormula = formula.nnf();

    List<Automaton<Pair<Integer, Integer>, ? extends RabinAcceptance>> automata = new ArrayList<>();

    for (Sigma2Pi2Pair disjunct : group(nnfFormula)) {
      var sigma2Automaton = OmegaAcceptanceCast.castExact(
          sigma2Portfolio.apply(disjunct.sigma2()).orElseGet(
              () -> DeterministicConstructionsPortfolio.coSafetySafety(disjunct.sigma2())),
          CoBuchiAcceptance.class);

      var normalisedSigma2Automaton = HashMapAutomaton.copyOf(minimize
          ? DcwMinimization.minimize(sigma2Automaton)
          : Views.dropStateLabels(sigma2Automaton).automaton());

      var pi2Automaton = OmegaAcceptanceCast.castExact(
          pi2Portfolio.apply(disjunct.pi2())
              .orElseGet(() -> DeterministicConstructionsPortfolio.safetyCoSafety(disjunct.pi2())),
          BuchiAcceptance.class);

      var normalisedPi2Automaton = HashMapAutomaton.copyOf(minimize
          ? DbwMinimization.minimize(pi2Automaton)
          : Views.dropStateLabels(pi2Automaton).automaton());

      // AcceptanceOptimizations.removeDeadStates(normalisedSigma2Automaton);
      // AcceptanceOptimizations.removeDeadStates(normalisedPi2Automaton);

      automata.add(OmegaAcceptanceCast.cast(
          BooleanOperations.intersection(normalisedSigma2Automaton, normalisedPi2Automaton),
          RabinAcceptance.class));
    }

    if (automata.isEmpty()) {
      return OmegaAcceptanceCast.castExact(
          EmptyAutomaton.of(nnfFormula.atomicPropositions(), AllAcceptance.INSTANCE),
          RabinAcceptance.class);
    }

    var automaton = HashMapAutomaton.copyOf(
        OmegaAcceptanceCast.castExact(
            BooleanOperations.deterministicUnion(automata),
            RabinAcceptance.class));

    AcceptanceOptimizations.removeDeadStates(automaton);
    AcceptanceOptimizations.transform(automaton);
    return automaton;
  }
}
