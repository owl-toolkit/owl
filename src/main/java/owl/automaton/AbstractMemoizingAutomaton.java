/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.Collections3;
import owl.collections.Either;

/**
 * This class provides a skeletal implementation of the {@code Automaton}
 * interface to minimize the effort required to implement this interface.
 *
 * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
 * transition relation, and the acceptance condition is fixed. It makes use of this
 * assumption by caching the set of states and by memoizing the transition relation
 * as {@link MtBdd}.
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
public abstract class AbstractMemoizingAutomaton<S, A extends EmersonLeiAcceptance>
  implements Automaton<S, A> {

  protected final A acceptance;
  protected final Set<S> initialStates;
  protected final BddSetFactory factory;
  protected final List<String> atomicPropositions;

  // Memoization.
  private boolean explorationCompleted;
  private final Map<S, MtBdd<Edge<S>>> memoizedEdgeTrees;
  private final Map<S, Set<Edge<S>>> memoizedEdges;

  private AbstractMemoizingAutomaton(
    List<String> atomicPropositions, Set<S> initialStates, A acceptance) {

    this(atomicPropositions,
      FactorySupplier.defaultSupplier().getBddSetFactory(),
      initialStates,
      acceptance);
  }

  private AbstractMemoizingAutomaton(
    List<String> atomicPropositions, BddSetFactory factory, Set<S> initialStates, A acceptance) {

    this.acceptance = acceptance;
    this.atomicPropositions = List.copyOf(atomicPropositions);
    this.initialStates = Set.copyOf(initialStates);
    this.factory = factory;

    if (this.initialStates.isEmpty()) {
      // TODO: add call to explorationCompleted() after constructor is finished.
      explorationCompleted = true;
      memoizedEdgeTrees = Map.of();
      memoizedEdges = Map.of();
    } else {
      explorationCompleted = false;
      memoizedEdgeTrees = new HashMap<>(Math.max(this.initialStates.size(), 16));
      memoizedEdges = new HashMap<>(Math.max(this.initialStates.size(), 16));

      // Mark initialStates as unexplored.
      for (S initialState : this.initialStates) {
        memoizedEdgeTrees.put(initialState, null);
      }
    }

    Preconditions.checkArgument(Collections3.isDistinct(this.atomicPropositions));
  }

  /**
   * Wrap any automaton into a MemoizingAutomaton to make use of the internal caching mechanism and
   * to guarantee immutability after full exploration. The reference to the backing automaton is
   * dropped once the automaton is completely explored.
   *
   * @param automaton the automaton
   * @param <S> foo
   * @param <A> bar
   * @return a caching instance.
   */
  public static <S, A extends EmersonLeiAcceptance> AbstractMemoizingAutomaton<S, A>
    memoizingAutomaton(Automaton<S, A> automaton) {

    if (automaton instanceof AbstractMemoizingAutomaton) {
      return (AbstractMemoizingAutomaton<S, A>) automaton;
    }

    return new AutomaticMemoizingAutomaton<>(automaton);
  }

  private static class AutomaticMemoizingAutomaton<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

    @Nullable
    private Automaton<S, A> backingAutomaton;

    public AutomaticMemoizingAutomaton(Automaton<S, A> automaton) {
      super(
        automaton.atomicPropositions(),
        automaton.factory(),
        automaton.initialStates(),
        automaton.acceptance());
      this.backingAutomaton = automaton;
    }

    @Override
    protected MtBdd<Edge<S>> edgeTreeImpl(S state) {
      return Objects.requireNonNull(backingAutomaton).edgeTree(state);
    }

    @Override
    protected void explorationCompleted() {
      backingAutomaton = null;
    }
  }

  @Override
  public final MtBdd<Edge<S>> edgeTree(S state) {
    var edgeTree = memoizedEdgeTrees.get(state);

    if (edgeTree != null) {
      return edgeTree;
    }

    edgeTree = edgeTreeImpl(state);
    memoizedEdgeTrees.put(state, edgeTree);

    @SuppressWarnings("unchecked")
    Edge<S>[] edges = edgeTree.flatValues().toArray(Edge[]::new);
    memoizedEdges.put(state, Set.of(edges));

    // Update the set of unexplored states.
    for (Edge<S> edge : edges) {
      memoizedEdgeTrees.putIfAbsent(edge.successor(), null);
    }

    return edgeTree;
  }

  @Override
  public final Map<Edge<S>, BddSet> edgeMap(S state) {
    return edgeTree(state).inverse(factory);
  }

  @Nullable
  @Override
  public final Edge<S> edge(S state, BitSet valuation) {
    return Iterables.getFirst(edges(state, valuation), null);
  }

  @Override
  public final Set<Edge<S>> edges(S state, BitSet valuation) {
    return edgeTree(state).get(valuation);
  }

  @Override
  public final Set<Edge<S>> edges(S state) {
    // Call edgeTree to ensure that the result is cached.
    edgeTree(state);
    return Objects.requireNonNull(memoizedEdges.get(state));
  }

  @Nullable
  @Override
  public final S successor(S state, BitSet valuation) {
    return Iterables.getFirst(successors(state, valuation), null);
  }

  @Override
  public final Set<S> successors(S state, BitSet valuation) {
    return Edges.successors(edgeTree(state).get(valuation));
  }

  @Override
  public final Set<S> successors(S state) {
    return Edges.successors(edges(state));
  }

  @Override
  public final A acceptance() {
    return acceptance;
  }

  @Override
  public final List<String> atomicPropositions() {
    return atomicPropositions;
  }

  @Override
  public final BddSetFactory factory() {
    return factory;
  }

  @Override
  public final S initialState() {
    return Automaton.super.initialState();
  }

  @Override
  public final Set<S> initialStates() {
    return initialStates;
  }

  @Override
  public final Set<S> states() {
    // Explore missing part of the state space.
    if (!explorationCompleted) {
      do {
        List<S> unexploredStates = new ArrayList<>();

        // Copy to avoid concurrent modification exception.
        memoizedEdgeTrees.forEach((state, tree) -> {
          if (tree == null) {
            unexploredStates.add(state);
          }
        });

        int s = unexploredStates.size();

        for (int i = 0; i < s; i++) {
          edgeTree(unexploredStates.get(i));
        }

        explorationCompleted = (s == 0);
      } while (!explorationCompleted);

      explorationCompleted();
    }

    return memoizedEdgeTrees.isEmpty()
      ? Set.of()
      : Collections.unmodifiableSet(memoizedEdgeTrees.keySet());
  }

  @Override
  public boolean is(Property property) {
    if (property != Property.COMPLETE && property != Property.SEMI_DETERMINISTIC) {
      return Automaton.super.is(property);
    }

    // force full exploration.
    states();

    if (property == Property.COMPLETE) {
      return !initialStates.isEmpty() && memoizedEdgeTrees.values().stream().allMatch(
        x -> x.values().stream().allMatch(y -> y.size() >= 1));
    }

    assert property == Property.SEMI_DETERMINISTIC;
    return memoizedEdgeTrees.values().stream().allMatch(
      x -> x.values().stream().allMatch(y -> y.size() <= 1));
  }

  boolean edgeTreePrecomputed(S state) {
    return memoizedEdgeTrees.get(state) != null;
  }

  protected abstract MtBdd<Edge<S>> edgeTreeImpl(S state);

  @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
  protected void explorationCompleted() {
    // do nothing. Subclasses can be notified that the transition relation is frozen.
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link MtBdd}.
   *
   * <p>This is the recommended implementation class.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgeTreeImplementation<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton<S, A> {

    public EdgeTreeImplementation(
      List<String> atomicPropositions, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, initialStates, acceptance);
    }

    public EdgeTreeImplementation(
      List<String> atomicPropositions, BddSetFactory factory, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, factory, initialStates, acceptance);
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link MtBdd}.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgeMapImplementation<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton<S, A> {

    public EdgeMapImplementation(
      List<String> atomicPropositions, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, initialStates, acceptance);
    }

    public EdgeMapImplementation(
      List<String> atomicPropositions, BddSetFactory factory, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, factory, initialStates, acceptance);
    }

    protected abstract Map<Edge<S>, BddSet> edgeMapImpl(S state);

    @Override
    protected final MtBdd<Edge<S>> edgeTreeImpl(S state) {
      return factory.toMtBdd(edgeMapImpl(state));
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link MtBdd}.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgesImplementation<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton<S, A> {

    public EdgesImplementation(
      List<String> atomicPropositions, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, initialStates, acceptance);
    }

    public EdgesImplementation(
      List<String> atomicPropositions, BddSetFactory factory, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, factory, initialStates, acceptance);
    }

    protected abstract Set<Edge<S>> edgesImpl(S state, BitSet valuation);

    protected BitSet stateAtomicPropositions(S state) {
      int atomicPropositionsSize = atomicPropositions().size();
      BitSet stateAtomicPropositions = new BitSet(atomicPropositionsSize);
      stateAtomicPropositions.set(0, atomicPropositionsSize);
      return stateAtomicPropositions;
    }

    @Override
    protected final MtBdd<Edge<S>> edgeTreeImpl(S state) {
      var stateAtomicPropositions = stateAtomicPropositions(state);
      return edgeTreeImplRecursive(state,
        new BitSet(),
        stateAtomicPropositions,
        stateAtomicPropositions.nextSetBit(0));
    }

    private MtBdd<Edge<S>> edgeTreeImplRecursive(
      S state, BitSet partialValuation, BitSet stateAtomicPropositions, int currentVariable) {

      int nextVariable = stateAtomicPropositions.nextSetBit(currentVariable + 1);

      if (currentVariable >= 0) {
        partialValuation.set(currentVariable);
        partialValuation.clear(currentVariable + 1, atomicPropositions().size());
        var trueChild
          = edgeTreeImplRecursive(state, partialValuation, stateAtomicPropositions, nextVariable);
        partialValuation.clear(currentVariable, atomicPropositions().size());
        var falseChild
          = edgeTreeImplRecursive(state, partialValuation, stateAtomicPropositions, nextVariable);
        return MtBdd.of(currentVariable, trueChild, falseChild);
      }

      return MtBdd.of(edgesImpl(state, (BitSet) partialValuation.clone()));
    }
  }

  /**
   * This class provides a skeletal implementation of the {@code Automaton}
   * interface to minimize the effort required to implement this interface.
   *
   * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
   * transition relation, and the acceptance condition is fixed. It makes use of this
   * assumption by caching the set of states and by memoizing the transition relation
   * as {@link MtBdd}.
   *
   * @param <S> the state type
   * @param <A> the acceptance condition type
   **/
  public abstract static class EdgeImplementation<S, A extends EmersonLeiAcceptance>
    extends EdgesImplementation<S, A> {

    public EdgeImplementation(
      List<String> atomicPropositions, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, initialStates, acceptance);
    }

    public EdgeImplementation(
      List<String> atomicPropositions, BddSetFactory factory, Set<S> initialStates, A acceptance) {

      super(atomicPropositions, factory, initialStates, acceptance);
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
   * as {@link MtBdd}.
   *
   * <p>This class assumes that there are two separate parts of the automaton with state type
   * A and B. Runs can move from A to B but not the other way.
   *
   * @param <A> the first state type
   * @param <B> the second state type
   * @param <C> the acceptance condition type
   **/
  public abstract static class
    PartitionedEdgeTreeImplementation<A, B, C extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton<Either<A, B>, C> {

    @Nullable
    // We only memoize edges for state-type B, since only they can be visited several times via
    // edgeTreeImpl.
    private Map<B, MtBdd<Edge<Either<A, B>>>> memoizedEdgesB = new HashMap<>();

    public PartitionedEdgeTreeImplementation(
      List<String> atomicPropositions,
      Set<? extends A> initialStatesA,
      Set<? extends B> initialStatesB,
      C acceptance) {

      super(
        atomicPropositions,
        Sets.union(
          Collections3.transformSet(initialStatesA, Either::left),
          Collections3.transformSet(initialStatesB, Either::right)),
        acceptance);
    }

    public PartitionedEdgeTreeImplementation(
      List<String> atomicPropositions,
      BddSetFactory factory,
      Set<? extends A> initialStatesA,
      Set<? extends B> initialStatesB,
      C acceptance) {

      super(
        atomicPropositions,
        factory,
        Sets.union(
          Collections3.transformSet(initialStatesA, Either::left),
          Collections3.transformSet(initialStatesB, Either::right)),
        acceptance);
    }

    @Override
    protected final MtBdd<Edge<Either<A, B>>> edgeTreeImpl(Either<A, B> state) {
      assert memoizedEdgesB != null : "edgeTreeImpl is never called after releasing the map.";

      switch (state.type()) {
        case LEFT:
          var aState = state.left();

          List<MtBdd<Edge<Either<A, B>>>> trees = new ArrayList<>();
          trees.add(edgeTreeImplA(aState).map(this::liftA));

          for (B bState : moveAtoB(aState)) {
            trees.add(memoizedEdgesB.computeIfAbsent(
              bState, x -> edgeTreeImplB(x).map(this::liftB)));
          }

          return MtBddOperations.union(trees).map(this::deduplicate);

        case RIGHT:
          var b = state.right();
          return memoizedEdgesB
            .computeIfAbsent(b, x -> edgeTreeImplB(x).map(this::liftB))
            .map(this::deduplicate);

        default:
          throw new AssertionError("unreachable");
      }
    }

    protected abstract MtBdd<Edge<A>> edgeTreeImplA(A state);

    protected abstract MtBdd<Edge<B>> edgeTreeImplB(B state);

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

    @Override
    protected void explorationCompleted() {
      memoizedEdgesB = null;
    }
  }
}
