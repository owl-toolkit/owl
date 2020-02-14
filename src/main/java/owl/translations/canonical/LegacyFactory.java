/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import java.util.BitSet;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;

/**
 * Legacy class giving other packages access to internals.
 */
@Deprecated
public final class LegacyFactory
  extends DeterministicConstructions.Base<EquivalenceClass, AllAcceptance> {

  public LegacyFactory(Factories factories) {
    super(factories, factories.eqFactory.of(BooleanConstant.TRUE), AllAcceptance.INSTANCE);
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

  public EquivalenceClass successor(EquivalenceClass clazz, BitSet valuation,
    EquivalenceClass environment) {
    EquivalenceClass state = successorInternal(clazz, valuation);
    return environment.implies(state) ? factory.of(BooleanConstant.TRUE) : state;
  }

  public EquivalenceClass initialStateInternal(EquivalenceClass clazz,
    EquivalenceClass environment) {
    EquivalenceClass state = clazz.unfold();
    return environment.implies(state) ? factory.of(BooleanConstant.TRUE) : state;
  }
}
