/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.ltl2ldba.breakpoint;

import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.visitors.Collector;
import owl.translations.ltl2ldba.AbstractJumpManager;
import owl.translations.ltl2ldba.FGSubstitution;
import owl.translations.ltl2ldba.Jump;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class GObligationsJumpManager extends AbstractJumpManager<GObligations> {
  private static final Logger logger = Logger.getLogger(GObligationsJumpManager.class.getName());
  private final Set<GObligations> obligations;

  private GObligationsJumpManager(EquivalenceClassFactory factory, Set<Configuration> optimisations,
    Set<GObligations> obligations, Set<Formula> modalOperators, Formula initialFormula) {
    super(optimisations, factory, modalOperators, initialFormula);
    this.obligations = Set.copyOf(obligations);
    logger.log(Level.FINE, () -> "The automaton has the following jumps: " + obligations);
  }

  public static GObligationsJumpManager build(Formula formula, EquivalenceClassFactory factory,
    Set<Configuration> optimisations) {
    EquivalenceClass initialState = factory.of(formula);
    Set<Formula> modalOperators = initialState.modalOperators();

    if (modalOperators.stream().allMatch(Fragments::isCoSafety)
      || modalOperators.stream().allMatch(Fragments::isSafety)) {
      return new GObligationsJumpManager(initialState.factory(), optimisations, Set.of(), Set.of(),
        BooleanConstant.TRUE);
    }

    Set<GObligations> jumps = createDisjunctionStream(initialState,
      GObligationsJumpManager::createGSetStream)
      .map(Gs -> GObligations.build(Gs, initialState.factory(), optimisations))
      .filter(Objects::nonNull)
      .collect(Collectors.toUnmodifiableSet());

    return new GObligationsJumpManager(initialState.factory(), optimisations, jumps,
      initialState.modalOperators(), formula);
  }

  private static Stream<Set<GOperator>> createGSetStream(Formula formula) {
    return Sets.powerSet(Collector.collectTransformedGOperators(formula)).stream();
  }

  private static boolean dependsOnExternalAtoms(EquivalenceClass remainder,
    GObligations obligation) {
    BitSet remainderAP = remainder.atomicPropositions();
    remainderAP.or(Collector.collectAtoms(remainder.modalOperators()));
    BitSet obligationAP = Collector.collectAtoms(obligation.gOperatorsRewritten());

    assert !remainderAP.isEmpty();
    assert !obligationAP.isEmpty();

    return !remainderAP.intersects(obligationAP);
  }

  @Override
  protected Set<Jump<GObligations>> computeJumps(EquivalenceClass state) {
    EquivalenceClass state2 = configuration.contains(Configuration.EAGER_UNFOLD)
      ? state
      : state.unfold();
    Set<GObligations> availableObligations = new HashSet<>();

    for (GObligations x : obligations) {
      BitSet obligationAtoms = Collector.collectAtoms(x.gOperators());
      BitSet supportAtoms = Collector.collectAtoms(state2.modalOperators());
      supportAtoms.or(state2.atomicPropositions());

      if (BitSets.isSubset(obligationAtoms, supportAtoms)) {
        availableObligations.add(x);
      }
    }

    Set<Jump<GObligations>> jumps = new HashSet<>();

    for (GObligations obligation : availableObligations) {
      FGSubstitution evaluateVisitor = new FGSubstitution(obligation.gOperators());
      EquivalenceClass remainder = state.substitute(x -> x.accept(evaluateVisitor));

      if (remainder.isFalse()) {
        continue;
      }

      if (obligation.getObligation().implies(remainder)) {
        jumps.add(buildJump(factory.getTrue(), obligation));
      } else if (!configuration.contains(Configuration.SUPPRESS_JUMPS)
        || !dependsOnExternalAtoms(remainder, obligation)) {
        jumps.add(buildJump(remainder, obligation));
      }
    }

    return jumps;
  }
}