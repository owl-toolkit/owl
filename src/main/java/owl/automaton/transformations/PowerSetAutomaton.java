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

import com.google.common.collect.ImmutableSet;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public class PowerSetAutomaton<S> implements Automaton<Set<S>, NoneAcceptance> {

  private final Automaton<S, NoneAcceptance> automaton;

  public PowerSetAutomaton(Automaton<S, NoneAcceptance> automaton) {
    this.automaton = automaton;
  }

  @Override
  public NoneAcceptance getAcceptance() {
    return automaton.getAcceptance();
  }

  @Override
  public ValuationSetFactory getFactory() {
    return automaton.getFactory();
  }

  @Override
  public Map<Set<S>, ValuationSet> getIncompleteStates() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Set<S>> getInitialStates() {
    return Collections.singleton(automaton.getInitialStates());
  }

  @Override
  public Collection<LabelledEdge<Set<S>>> getLabelledEdges(Set<S> state) {
    List<LabelledEdge<Set<S>>> edges = new ArrayList<>();

    for (BitSet valuation : BitSets.powerSet(getFactory().getSize())) {
      edges.add(new LabelledEdge<>(Edges.create(getSuccessor(state, valuation)),
          getFactory().createValuationSet(valuation)));
    }

    return edges;
  }

  @Override
  public Set<Set<S>> getStates() {
    return getReachableStates(getInitialStates());
  }

  @Override
  public Set<S> getSuccessor(Set<S> state, BitSet valuation) {
    ImmutableSet.Builder<S> builder = ImmutableSet.builder();
    state.forEach(s -> builder.addAll(automaton.getSuccessors(s, valuation)));
    return builder.build();
  }

  @Override
  public Set<Set<S>> getSuccessors(Set<S> state, BitSet valuation) {
    return Collections.singleton(getSuccessor(state, valuation));
  }

  @Override
  public Set<Set<S>> getSuccessors(Set<S> state) {
    ImmutableSet.Builder<Set<S>> builder = ImmutableSet.builder();

    for (BitSet valuation : BitSets.powerSet(getFactory().getSize())) {
      builder.add(getSuccessor(state, valuation));
    }

    return builder.build();
  }

  @Override
  public List<String> getVariables() {
    return automaton.getVariables();
  }

  @Override
  public void setVariables(List<String> variables) {
    automaton.setVariables(variables);
  }
}
