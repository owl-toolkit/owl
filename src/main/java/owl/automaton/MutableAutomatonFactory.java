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

package owl.automaton;

import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.Automaton.EdgeMapVisitor;
import owl.automaton.Automaton.EdgeVisitor;
import owl.automaton.Automaton.Visitor;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class MutableAutomatonFactory {
  private MutableAutomatonFactory() {
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> copy(Automaton<S, A> source) {
    MutableAutomaton<S, A> target = new HashMapAutomaton<>(source.factory(), source.acceptance());
    copy(source, target);
    assert source.states().equals(target.states());
    return target;
  }

  public static <S> void copy(Automaton<S, ?> source, MutableAutomaton<S, ?> target) {
    source.initialStates().forEach(target::addInitialState);
    source.accept((Visitor<S>) new CopyVisitor<>(target));
    target.trim(); // Cannot depend on iteration order, thus we need to trim().
    target.name(source.name());
  }

  /**
   * Creates an empty automaton with given acceptance condition. The {@code valuationSetFactory} is
   * used as transition backend.
   *
   * @param acceptance The acceptance of the new automaton.
   * @param vsFactory The alphabet.
   * @param <S> The states of the automaton.
   * @param <A> The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> create(A acceptance,
    ValuationSetFactory vsFactory) {
    return new HashMapAutomaton<>(vsFactory, acceptance);
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> create(A acceptance,
    ValuationSetFactory vsFactory, Collection<S> initialStates,
    BiFunction<S, BitSet, Edge<S>> successors, Function<S, BitSet> alphabet) {
    MutableAutomaton<S, A> automaton = new HashMapAutomaton<>(vsFactory, acceptance);
    initialStates.forEach(automaton::addInitialState);
    Set<S> exploredStates = new HashSet<>(initialStates);
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    int alphabetSize = vsFactory.alphabetSize();

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();

      BitSet sensitiveAlphabet = alphabet.apply(state);
      Set<BitSet> bitSets = sensitiveAlphabet == null
        ? BitSets.powerSet(alphabetSize)
        : BitSets.powerSet(sensitiveAlphabet);

      for (BitSet valuation : bitSets) {
        Edge<S> edge = successors.apply(state, valuation);

        if (edge == null) {
          continue;
        }

        ValuationSet valuationSet;

        if (sensitiveAlphabet == null) {
          valuationSet = vsFactory.of(valuation);
        } else {
          valuationSet = vsFactory.of(valuation, sensitiveAlphabet);
        }

        S successorState = edge.successor();

        if (exploredStates.add(successorState)) {
          workQueue.add(successorState);
        }

        automaton.addEdge(state, valuationSet, edge);
      }
    }

    return automaton;
  }

  private static class CopyVisitor<S> implements EdgeVisitor<S>, EdgeMapVisitor<S> {
    private final MutableAutomaton<S, ?> target;

    private CopyVisitor(MutableAutomaton<S, ?> target) {
      this.target = target;
    }

    @Override
    public void visit(S state, BitSet valuation, Edge<S> edge) {
      target.addEdge(state, valuation, edge);
    }

    @Override
    public void visit(S state, Map<Edge<S>, ValuationSet> edgeMap) {
      edgeMap.forEach((x, y) -> target.addEdge(state, y, x));
    }

    @Override
    public void enter(S state) {
      target.addState(state);
    }
  }
}
