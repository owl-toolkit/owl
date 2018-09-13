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

package owl.translations.canonical;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

import java.util.BitSet;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;

/**
 * Legacy class giving other packages access to internals.
 */
@Deprecated
public final class LegacyFactory
  extends DeterministicConstructions.Base<EquivalenceClass, NoneAcceptance> {
  private final boolean removeRedundantObligations;

  public LegacyFactory(Factories factories, Set<Configuration> configuration) {
    super(factories, configuration.contains(Configuration.EAGER_UNFOLD));
    removeRedundantObligations = configuration.contains(Configuration.OPTIMISED_STATE_STRUCTURE);
  }

  @Override
  public EquivalenceClass onlyInitialState() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public Edge<EquivalenceClass> edge(EquivalenceClass state, BitSet valuation) {
    var successor = successorInternal(state, valuation);
    return successor.isFalse() ? null : Edge.of(successor);
  }

  @Override
  public ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass state) {
    return successorTreeInternal(state, x -> x.isFalse() ? Set.of() : Set.of(Edge.of(x)));
  }

  private EquivalenceClass removeRedundantObligations(EquivalenceClass state,
    EquivalenceClass environment) {
    if (removeRedundantObligations && environment.implies(state)) {
      return factory.getTrue();
    }

    return state;
  }

  @Override
  public NoneAcceptance acceptance() {
    return NoneAcceptance.INSTANCE;
  }

  public EquivalenceClass successor(EquivalenceClass clazz, BitSet valuation,
    EquivalenceClass environment) {
    return removeRedundantObligations(successorInternal(clazz, valuation), environment);
  }

  @Nullable
  public EquivalenceClass[] successors(EquivalenceClass[] clazz, BitSet valuation,
    EquivalenceClass environment) {
    EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

    for (int i = clazz.length - 1; i >= 0; i--) {
      successors[i] = successor(clazz[i], valuation, environment);

      if (successors[i].isFalse()) {
        return null;
      }
    }

    return successors;
  }

  @Override
  // Change visibility!
  @SuppressWarnings("PMD.UselessOverridingMethod")
  public EquivalenceClass initialStateInternal(EquivalenceClass clazz) {
    return super.initialStateInternal(clazz);
  }

  public EquivalenceClass initialStateInternal(EquivalenceClass clazz,
    EquivalenceClass environment) {
    return removeRedundantObligations(initialStateInternal(clazz), environment);
  }

  public BitSet sensitiveAlphabet(EquivalenceClass clazz) {
    if (eagerUnfold) {
      return clazz.atomicPropositions();
    } else {
      return clazz.unfold().atomicPropositions();
    }
  }
}
