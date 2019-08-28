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

import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.Either;
import owl.collections.ValuationTree;
import owl.collections.ValuationTrees;

public abstract class TwoPartAutomaton<A, B, C extends OmegaAcceptance>
  extends AbstractCachedStatesAutomaton<Either<A, B>, C>
  implements EdgeTreeAutomatonMixin<Either<A, B>, C> {

  @Override
  public final Set<Either<A, B>> initialStates() {
    return Sets.union(
      Collections3.transformSet(initialStatesA(), Either::left),
      Collections3.transformSet(initialStatesB(), Either::right));
  }

  @Override
  public final Set<Edge<Either<A, B>>> edges(Either<A, B> state, BitSet valuation) {
    return state.map(a -> {
      var edges = moveAtoB(a).stream()
        .flatMap(b -> liftB(edgesB(b, valuation)).stream())
        .collect(Collectors.toSet());
      edges.addAll(liftA(edgesA(a, valuation)));
      return deduplicate(edges);
    }, b -> deduplicate(liftB(edgesB(b, valuation))));
  }

  @Override
  public final ValuationTree<Edge<Either<A, B>>> edgeTree(Either<A, B> state) {
    return state.map(a -> {
      var trees = moveAtoB(a).stream()
        .map(x -> edgeTreeB(x).map(this::liftB))
        .collect(Collectors.toSet());
      trees.add(edgeTreeA(a).map(this::liftA));
      return ValuationTrees.union(trees).map(this::deduplicate);
    }, b -> edgeTreeB(b).map(x -> deduplicate(liftB(x))));
  }

  protected abstract Set<A> initialStatesA();

  protected abstract Set<B> initialStatesB();

  protected Set<Edge<A>> edgesA(A state, BitSet valuation) {
    return edgeTreeA(state).get(valuation);
  }

  protected Set<Edge<B>> edgesB(B state, BitSet valuation) {
    return edgeTreeB(state).get(valuation);
  }

  protected abstract ValuationTree<Edge<A>> edgeTreeA(A state);

  protected abstract ValuationTree<Edge<B>> edgeTreeB(B state);

  protected abstract Set<B> moveAtoB(A state);

  protected Set<Edge<Either<A, B>>> deduplicate(Set<Edge<Either<A, B>>> edges) {
    return edges;
  }

  private Set<Edge<Either<A, B>>> liftA(Set<Edge<A>> aEdges) {
    return Collections3.transformSet(aEdges,
      edge -> edge.withSuccessor(Either.left(edge.successor())));
  }

  private Set<Edge<Either<A, B>>> liftB(Set<Edge<B>> aEdges) {
    return Collections3.transformSet(aEdges,
      edge -> edge.withSuccessor(Either.right(edge.successor())));
  }
}
