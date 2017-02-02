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

package owl.translations.ltl2ldba;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.AutomatonState;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.ng.NondetInitialComponent;

public class InitialComponentState implements AutomatonState<InitialComponentState> {
  private final EquivalenceClass clazz;
  private final InitialComponent<?, ?> parent;
  // TODO: Move this map to parent.
  private Set<?> jumps;

  public InitialComponentState(InitialComponent<?, ?> parent, EquivalenceClass clazz) {
    this.parent = parent;
    this.clazz = clazz;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    InitialComponentState that = (InitialComponentState) o;
    return Objects.equals(clazz, that.clazz);
  }

  public void free() {
    clazz.free();
  }

  public EquivalenceClass getClazz() {
    return clazz;
  }

  @Nonnull
  @Override
  public BitSet getSensitiveAlphabet() {
    return parent.factory.getSensitiveAlphabet(clazz);
  }

  @Nullable
  @Override
  public Edge<InitialComponentState> getSuccessor(@Nonnull BitSet valuation) {
    EquivalenceClass successorClass;

    if (parent instanceof NondetInitialComponent) {
      successorClass = parent.factory.getNondetSuccessor(clazz, valuation);
    } else {
      successorClass = parent.factory.getSuccessor(clazz, valuation);
    }

    // TODO: Move this map to parent.
    if (jumps == null) {
      jumps = parent.selector.select(clazz);
    }

    // Suppress edge, if successor is a non-accepting state
    if (successorClass.isFalse() || !jumps.contains(null)) {
      return null;
    }

    InitialComponentState successor = new InitialComponentState(parent, successorClass);
    return successorClass.isTrue()
      ? Edges.create(successor, parent.getAcceptBitSet())
      : Edges.create(successor);
  }

  @Override
  public int hashCode() {
    return clazz.hashCode();
  }

  @Override
  public String toString() {
    return clazz.toString();
  }
}
