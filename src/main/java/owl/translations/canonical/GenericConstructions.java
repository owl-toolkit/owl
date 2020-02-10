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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.Either;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

public final class GenericConstructions {

  private GenericConstructions() {
  }

  public static <S, A extends OmegaAcceptance> Automaton<Either<Integer, S>, A> delay(
    Automaton<S, A> automaton, int steps) {
    Preconditions.checkArgument(steps > 0, "steps needs to be positive.");
    List<Either<Integer, S>> delayingStates = IntStream.range(0, steps)
      .mapToObj(Either::<Integer, S>left).collect(Collectors.toUnmodifiableList());
    Set<Edge<Either<Integer, S>>> initialStateEdges = Set.copyOf(
      Collections3.transformSet(automaton.initialStates(), x -> Edge.of(Either.right(x))));

    return new Automaton<>() {
      @Override
      public A acceptance() {
        return automaton.acceptance();
      }

      @Override
      public ValuationSetFactory factory() {
        return automaton.factory();
      }

      @Override
      public Set<Either<Integer, S>> initialStates() {
        return Set.of(delayingStates.get(steps - 1));
      }

      @Override
      public Set<Either<Integer, S>> states() {
        return Sets.union(
          Set.copyOf(delayingStates), Collections3.transformSet(automaton.states(), Either::right));
      }

      @Override
      public Set<Edge<Either<Integer, S>>> edges(Either<Integer, S> state, BitSet valuation) {
        return state.map(this::next, s -> lift(automaton.edges(s, valuation)));
      }

      @Override
      public Map<Edge<Either<Integer, S>>, ValuationSet> edgeMap(Either<Integer, S> state) {
        return state.map(this::nextMap, y -> lift(automaton.edgeMap(y)));
      }

      @Override
      public ValuationTree<Edge<Either<Integer, S>>> edgeTree(Either<Integer, S> state) {
        return state.map(this::nextTree, s -> lift(automaton.edgeTree(s)));
      }

      private Set<Edge<Either<Integer, S>>> next(int index) {
        if (index > 0) {
          return Set.of(Edge.of(delayingStates.get(index - 1)));
        }

        return initialStateEdges;
      }

      private Map<Edge<Either<Integer, S>>, ValuationSet> nextMap(int index) {
        return Maps.toMap(next(index), z -> factory().universe());
      }

      private ValuationTree<Edge<Either<Integer, S>>> nextTree(int index) {
        return ValuationTree.of(next(index));
      }

      private Set<Edge<Either<Integer, S>>> lift(Set<Edge<S>> edges) {
        return Collections3.transformSet(edges,
          edge -> edge.withSuccessor(Either.right(edge.successor())));
      }

      private Map<Edge<Either<Integer, S>>, ValuationSet> lift(Map<Edge<S>, ValuationSet> edgeMap) {
        return Collections3.transformMap(edgeMap,
          edge -> edge.withSuccessor(Either.right(edge.successor())));
      }

      private ValuationTree<Edge<Either<Integer, S>>> lift(ValuationTree<Edge<S>> edgeTree) {
        return edgeTree.map(this::lift);
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return automaton.preferredEdgeAccess();
      }
    };
  }
}
