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

package owl.automaton.transformations;

import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

public abstract class ForwardingMutableAutomaton<S, A extends OmegaAcceptance,
  B extends OmegaAcceptance> extends ForwardingAutomaton<S, A, B, MutableAutomaton<S, B>>
  implements MutableAutomaton<S, A> {

  protected ForwardingMutableAutomaton(MutableAutomaton<S, B> automaton) {
    super(automaton);
  }

  @Override
  public void addEdge(S source, BitSet valuation, Edge<? extends S> edge) {
    automaton.addEdge(source, valuation, edge);
  }

  @Override
  public void addEdge(S source, ValuationSet valuations, Edge<? extends S> edge) {
    automaton.addEdge(source, valuations, edge);
  }

  @Override
  public void addInitialStates(Collection<? extends S> states) {
    automaton.addInitialStates(states);
  }

  @Override
  public void addStates(Collection<? extends S> states) {
    automaton.addStates(states);
  }

  @Override
  public void free() {
    automaton.free();
  }

  @Override
  public Map<S, ValuationSet> getIncompleteStates() {
    return automaton.getIncompleteStates();
  }

  @Override
  public Set<S> getStates() {
    return automaton.getStates();
  }

  @Override
  public void remapAcceptance(Set<? extends S> states, IntUnaryOperator transformer) {
    throw new UnsupportedOperationException("Not supported");
  }

  @Override
  public void remapAcceptance(BiFunction<S, Edge<S>, BitSet> f) {
    throw new UnsupportedOperationException("Not supported");
  }

  @Override
  public void remapAcceptance(Set<? extends S> states, BiFunction<S, Edge<S>, BitSet> f) {
    throw new UnsupportedOperationException("Not supported");
  }

  @Override
  public void remapEdges(Set<? extends S> states, BiFunction<S, Edge<S>, Edge<S>> f) {
    automaton.remapEdges(states, f);
  }

  @Override
  public void removeEdge(S source, BitSet valuation, S destination) {
    automaton.removeEdge(source, valuation, destination);
  }

  @Override
  public void removeEdge(S source, ValuationSet valuations, S destination) {
    automaton.removeEdge(source, valuations, destination);
  }

  @Override
  public void removeEdges(S source, S destination) {
    automaton.removeEdges(source, destination);
  }

  @Override
  public boolean removeStates(Collection<? extends S> states) {
    return automaton.removeStates(states);
  }

  @Override
  public boolean removeStates(Predicate<S> states) {
    return automaton.removeStates(states);
  }

  @Override
  public void removeUnreachableStates(Collection<? extends S> start,
    Consumer<S> removedStatesConsumer) {
    automaton.removeUnreachableStates(start, removedStatesConsumer);
  }

  @Override
  public void setInitialStates(Collection<? extends S> states) {
    automaton.setInitialStates(states);
  }
}
