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

package owl.automaton;

import java.util.BitSet;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class MutableAutomatonFactory {
  private MutableAutomatonFactory() {
  }

  /**
   * Creates an empty automaton with given acceptance condition. The {@code valuationSetFactory} is
   * used as transition backend.
   *
   * @param acceptance
   *     The acceptance of the new automaton.
   * @param valuationSetFactory
   *     The transition valuation set factory
   * @param <S>
   *     The states of the automaton.
   * @param <A>
   *     The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> createMutableAutomaton(
    A acceptance, ValuationSetFactory valuationSetFactory) {
    return new HashMapAutomaton<>(valuationSetFactory, acceptance);
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> createMutableAutomaton(
    A acceptance, ValuationSetFactory valuationSetFactory, Collection<S> initialStates,
    BiFunction<S, BitSet, Edge<S>> successors, Function<S, BitSet> alphabet) {
    MutableAutomaton<S, A> automaton = createMutableAutomaton(acceptance, valuationSetFactory);
    AutomatonUtil.exploreDeterministic(automaton, initialStates, successors, alphabet);
    automaton.setInitialStates(initialStates);
    return automaton;
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> createMutableAutomaton(
    Automaton<S, A> automaton) {
    return createMutableAutomaton(automaton.getAcceptance(),
      automaton.getFactory(), automaton.getInitialStates(), automaton::getEdge,
      (x) -> null);
  }

  public abstract static class ForwardingMutableAutomaton<S, A extends OmegaAcceptance,
    B extends OmegaAcceptance>
    extends AutomatonFactory.ForwardingAutomaton<S, A, B, MutableAutomaton<S, B>>
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
    public void remapEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> f) {
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
    public boolean removeStates(Predicate<? super S> states) {
      return automaton.removeStates(states);
    }

    @Override
    public void removeUnreachableStates(Collection<? extends S> start,
      Consumer<? super S> removedStatesConsumer) {
      automaton.removeUnreachableStates(start, removedStatesConsumer);
    }

    @Override
    public void setInitialStates(Collection<? extends S> states) {
      automaton.setInitialStates(states);
    }
  }
}
