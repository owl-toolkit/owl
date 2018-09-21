/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.WOperator;
import owl.translations.ltl2ldba.AbstractJumpManager;
import owl.translations.ltl2ldba.FGSubstitution;
import owl.translations.ltl2ldba.Jump;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class GObligationsJumpManager extends AbstractJumpManager<GObligations> {
  private static final Logger logger = Logger.getLogger(GObligationsJumpManager.class.getName());
  private final Set<GObligations> obligations;

  private GObligationsJumpManager(Factories factories, Set<Configuration> optimisations,
    Set<GObligations> obligations, Set<Formula.ModalOperator> modalOperators,
    Formula initialFormula) {
    super(optimisations, factories, modalOperators, initialFormula);
    this.obligations = Set.copyOf(obligations);
    logger.log(Level.FINE, () -> "The automaton has the following jumps: " + obligations);
  }

  public static GObligationsJumpManager build(Formula formula, Factories factories,
    Set<Configuration> optimisations) {
    EquivalenceClass initialState = factories.eqFactory.of(formula);
    Set<Formula.ModalOperator> modalOperators = initialState.modalOperators();

    if (modalOperators.stream().allMatch(SyntacticFragment.CO_SAFETY::contains)
      || modalOperators.stream().allMatch(SyntacticFragment.SAFETY::contains)) {
      return new GObligationsJumpManager(factories, optimisations, Set.of(), Set.of(),
        BooleanConstant.TRUE);
    }

    Set<GObligations> jumps = createDisjunctionStream(initialState,
      GObligationsJumpManager::createGSetStream)
      .map(Gs -> GObligations.build(Gs, factories, optimisations))
      .filter(Objects::nonNull)
      .collect(Collectors.toUnmodifiableSet());

    return new GObligationsJumpManager(factories, optimisations, jumps,
      modalOperators, formula);
  }

  private static Stream<Set<GOperator>> createGSetStream(Formula formula) {
    Set<GOperator> gOperators = formula.subformulas(
      x -> x instanceof GOperator || x instanceof ROperator || x instanceof WOperator,
      x -> {
        if (x instanceof ROperator) {
          return new GOperator(((ROperator) x).right);
        }

        if (x instanceof WOperator) {
          return new GOperator(((WOperator) x).left);
        }

        return (GOperator) x;
      });

    return Sets.powerSet(gOperators).stream();
  }

  private static boolean dependsOnExternalAtoms(EquivalenceClass remainder,
    GObligations obligation) {
    BitSet remainderAP = remainder.atomicPropositions(true);
    BitSet atoms = new BitSet();
    obligation.gOperatorsRewritten.forEach(x -> atoms.or(x.atomicPropositions(true)));

    assert !remainderAP.isEmpty();
    assert !atoms.isEmpty();

    return !remainderAP.intersects(atoms);
  }

  @Override
  protected Set<Jump<GObligations>> computeJumps(EquivalenceClass state) {
    EquivalenceClass state2 = configuration.contains(Configuration.EAGER_UNFOLD)
      ? state
      : state.unfold();
    Set<GObligations> availableObligations = new HashSet<>();

    for (GObligations x : obligations) {
      BitSet stateAP = state2.atomicPropositions(true);
      BitSet obligationAP = new BitSet();
      x.gOperators.forEach(x2 -> obligationAP.or(x2.atomicPropositions(true)));

      if (BitSets.isSubset(obligationAP, stateAP)) {
        availableObligations.add(x);
      }
    }

    Set<Jump<GObligations>> jumps = new HashSet<>();

    for (GObligations obligation : availableObligations) {
      FGSubstitution evaluateVisitor = new FGSubstitution(obligation.gOperators);
      EquivalenceClass remainder = state.substitute(x -> x.accept(evaluateVisitor));

      if (remainder.isFalse()) {
        continue;
      }

      if (obligation.getObligation().implies(remainder)) {
        jumps.add(buildJump(factories.eqFactory.getTrue(), obligation));
      } else if (!configuration.contains(Configuration.SUPPRESS_JUMPS)
        || !dependsOnExternalAtoms(remainder, obligation)) {
        jumps.add(buildJump(remainder, obligation));
      }
    }

    return jumps;
  }
}