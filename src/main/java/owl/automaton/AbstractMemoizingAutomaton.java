/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import static owl.automaton.Automaton.PreferredEdgeAccess.EDGES;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_MAP;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_TREE;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.ValuationSet;
import owl.bdd.ValuationSetFactory;
import owl.collections.Collections3;
import owl.collections.Either;
import owl.collections.ValuationTree;
import owl.collections.ValuationTrees;

/**
 * This class provides a skeletal implementation of the {@code Automaton}
 * interface to minimize the effort required to implement this interface.
 *
 * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
 * transition relation, and the acceptance condition is fixed. It makes use of this
 * assumption by caching the set of states and by memoizing the transition relation
 * as {@link owl.collections.ValuationTree}.
 *
 * <p>Depending on the computation of the transition relation different sub-classes are
 * available:
 * {@link owl.automaton.AbstractMemoizingAutomaton.EdgeImplementation},
 * {@link owl.automaton.AbstractMemoizingAutomaton.EdgesImplementation},
 * {@link owl.automaton.AbstractMemoizingAutomaton.EdgeTreeImplementation}, and
 * {@link owl.automaton.AbstractMemoizingAutomaton.EdgeMapImplementation}.
 * It is recommended to extend
 * {@link owl.automaton.AbstractMemoizingAutomaton.EdgeTreeImplementation}.
 *
 * @param <S> the state type
 * @param <A> the acceptance condition type
 **/
public abstract class AbstractMemoizingAutomaton<S, A extends OmegaAcceptance>
  implements Automaton<S, A> {

  private static final List<PreferredEdgeAccess> ACCESS_MODES = List.of(EDGE_TREE, EDGE_MAP, EDGES);

  protected final A acceptance;
  protected final Set<S> initialStates;
  protected final ValuationSetFactory factory;

  // Memoization.
  private final Set<S> unexploredStates;
  private Map<S, ValuationTree<Edge<S>>> memoizedEdges = new HashMap<>();

  private AbstractMemoizingAutomaton(
    ValuationSetFactory factory, Set<S> initialStates, A acceptance) {

    this.acceptance = acceptance;
    this.initialStates = Set.copyOf(initialStates);
    this.factory = factory;
    this.unexploredStates = new HashSet<>(this.initialStates);
  }

  @Override
  public final ValuationTree<Edge<S>> edgeTree(S state) {
    var edgeTree = memoizedEdges.get(state);

    if (edgeTree != null) {
      return edgeTree;
    }

    edgeTree = edgeTreeImpl(state);
    memoizedEdges.put(state, edgeTree);

    // Update the set of unexplored states.
    unexploredStates.addAll(edgeTree.flatValues(Edge::successor));
    unexploredStates.removeAll(memoizedEdges.keySet());
    return edgeTree;
  }

  @Nullable
  @Override
  public final Edge<S> edge(S state, BitSet valuation) {
    return Iterables.getFirst(edgeTree(state).get(valuation), null);
  }

  @Override
  public final Map<Edge<S>, ValuationSet> edgeMap(S state) {
    return edgeTree(state).inverse(factory);
  }

  @Override
  public final Set<Edge<S>> edges(S state) {
    return edgeTree(state).flatValues();
  }

  @Override
  public final Set<Edge<S>> edges(S state, BitSet valuation) {
    return edgeTree(state).get(valuation);
  }

  @Override
  public final Set<S> successors(S state) {
    return edgeTree(state).flatValues(Edge::successor);
  }

  @Override
  public final Set<S> successors(S state, BitSet valuation) {
    return Edges.successors(edgeTree(state).get(valuation));
  }

  @Nullable
  @Override
  public final S successor(S state, BitSet valuation) {
    return Iterables.getFirst(Edges.successors(edgeTree(state).get(valuation)), null);
  }

  @Override
  public final List<PreferredEdgeAccess> preferredEdgeAccess() {
    return ACCESS_MODES;
  }

  @Override
  public final A acceptance() {
    return acceptance;
  }

  @Override
  public final ValuationSetFactory factory() {
    return factory;
  }

  @Override
  public final S onlyInitialState() {
    return Automaton.super.onlyInitialState();
  }

  @Override
  public final Set<S> initialStates() {
    return initialStates;
  }

  @Override
  public final Set<S> states() {
    while (!unexploredStates.isEmpty()) {
      for (Object state : unexploredStates.toArray()) {
        // Work-around for the inability to generate a type-safe array.
        @SuppressWarnings({"unchecked", "unused"})
        var unused = edgeTree((S) state);
      }
    }

    freezeMemoizedEdges();
    return memoizedEdges.keySet();
  }

  protected abstract ValuationTree<Edge<S>> edgeTreeImpl(S state);

  private void freezeMemoizedEdges() {
    Preconditions.checkState(unexploredStates.isEmpty());
    memoizedEdges = Map.copyOf(memoizedEdges);
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link owl.collections.ValuationTree}.
   *
   * <p>This is the recommended implementation class.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgeTreeImplementation<S, A extends OmegaAcceptance>
    extends AbstractMemoizingAutomaton<S, A> {

    public EdgeTreeImplementation(
      ValuationSetFactory factory, Set<S> initialStates, A acceptance) {

      super(factory, initialStates, acceptance);
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link owl.collections.ValuationTree}.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgeMapImplementation<S, A extends OmegaAcceptance>
    extends AbstractMemoizingAutomaton<S, A> {

    public EdgeMapImplementation(
      ValuationSetFactory factory, Set<S> initialStates, A acceptance) {

      super(factory, initialStates, acceptance);
    }

    protected abstract Map<Edge<S>, ValuationSet> edgeMapImpl(S state);

    @Override
    protected final ValuationTree<Edge<S>> edgeTreeImpl(S state) {
      return factory.toValuationTree(edgeMapImpl(state));
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link owl.collections.ValuationTree}.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgesImplementation<S, A extends OmegaAcceptance>
    extends AbstractMemoizingAutomaton<S, A> {

    public EdgesImplementation(
      ValuationSetFactory factory, Set<S> initialStates, A acceptance) {

      super(factory, initialStates, acceptance);
    }

    protected abstract Set<Edge<S>> edgesImpl(S state, BitSet valuation);

    @Override
    protected final ValuationTree<Edge<S>> edgeTreeImpl(S state) {
      return edgeTreeImplRecursive(state, new BitSet(), 0);
    }

    private ValuationTree<Edge<S>> edgeTreeImplRecursive(
      S state, BitSet partialValuation, int currentVariable) {

      if (currentVariable < factory.atomicPropositions().size()) {
        partialValuation.set(currentVariable);
        var trueChild
          = edgeTreeImplRecursive(state, partialValuation, currentVariable + 1);
        partialValuation.clear(currentVariable);
        var falseChild
          = edgeTreeImplRecursive(state, partialValuation, currentVariable + 1);
        return ValuationTree.of(currentVariable, trueChild, falseChild);
      }

      assert currentVariable == factory.atomicPropositions().size();
      return ValuationTree.of(edgesImpl(state, (BitSet) partialValuation.clone()));
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link owl.collections.ValuationTree}.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgeImplementation<S, A extends OmegaAcceptance>
    extends EdgesImplementation<S, A> {

    public EdgeImplementation(
      ValuationSetFactory factory, Set<S> initialStates, A acceptance) {

      super(factory, initialStates, acceptance);
    }

    @Nullable
    protected abstract Edge<S> edgeImpl(S state, BitSet valuation);

    @Override
    protected final Set<Edge<S>> edgesImpl(S state, BitSet valuation) {
      var edge = edgeImpl(state, valuation);
      return edge == null ? Set.of() : Set.of(edge);
    }

    @Override
    public final boolean is(Property property) {
      return property == Property.SEMI_DETERMINISTIC
        || property == Property.LIMIT_DETERMINISTIC
        || super.is(property);
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link owl.collections.ValuationTree}.
   *
   * <p>This class assumes that there are two separate parts of the automaton with state type
   * A and B. Runs can move from A to B but not the other way.
   *
   * @param <A> the first state type
   * @param <B> the second state type
   * @param <C> the acceptance condition type
   **/
  public abstract static class PartitionedEdgeTreeImplementation<A, B, C extends OmegaAcceptance>
    extends AbstractMemoizingAutomaton<Either<A, B>, C> {

    public PartitionedEdgeTreeImplementation(
      ValuationSetFactory factory, Set<A> initialStatesA, Set<B> initialStatesB, C acceptance) {

      super(factory,
        Sets.union(
          Collections3.transformSet(initialStatesA, Either::left),
          Collections3.transformSet(initialStatesB, Either::right)),
        acceptance);
    }

    @Override
    protected final ValuationTree<Edge<Either<A, B>>> edgeTreeImpl(Either<A, B> state) {
      return state.map(a -> {
        var trees = moveAtoB(a).stream()
          .map(x -> edgeTreeImplB(x).map(this::liftB))
          .collect(Collectors.toSet());
        trees.add(edgeTreeImplA(a).map(this::liftA));
        return ValuationTrees.union(trees).map(this::deduplicate);
      }, b -> edgeTreeImplB(b).map(x -> deduplicate(liftB(x))));
    }

    protected abstract ValuationTree<Edge<A>> edgeTreeImplA(A state);

    protected abstract ValuationTree<Edge<B>> edgeTreeImplB(B state);

    protected abstract Set<B> moveAtoB(A state);

    protected Set<Edge<Either<A, B>>> deduplicate(Set<Edge<Either<A, B>>> edges) {
      return edges;
    }

    private Set<Edge<Either<A, B>>> liftA(Set<Edge<A>> aEdges) {
      return Collections3.transformSet(aEdges, edge -> edge.mapSuccessor(Either::left));
    }

    private Set<Edge<Either<A, B>>> liftB(Set<Edge<B>> bEdges) {
      return Collections3.transformSet(bEdges, edge -> edge.mapSuccessor(Either::right));
    }
  }
}
