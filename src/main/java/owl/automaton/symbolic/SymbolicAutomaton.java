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

package owl.automaton.symbolic;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableAllocation.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableAllocation.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableAllocation.VariableType.STATE;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableAllocation.VariableType.SUCCESSOR_STATE;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;

/**
 * An automaton over infinite words.
 *
 * <p>This class provides a symbolic automaton representation.
 *
 * @param <A> the acceptance class
 */
@AutoValue
public abstract class SymbolicAutomaton<A extends EmersonLeiAcceptance> {

  public abstract List<String> atomicPropositions();

  public abstract BddSet initialStates();

  public abstract BddSet transitionRelation();

  public abstract A acceptance();

  public abstract VariableAllocation variableAllocation();

  /**
   * Package-private constructor that is only available to trusted implementations.
   */
  static <A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    List<String> atomicPropositions,
    BddSet initialStates,
    BddSet transitionRelation,
    A acceptance,
    VariableAllocation variableAllocation) {

    checkArgument(initialStates.factory() == transitionRelation.factory());

    return new AutoValue_SymbolicAutomaton<>(
      List.copyOf(atomicPropositions),
      initialStates,
      transitionRelation,
      acceptance,
      variableAllocation
    );
  }

  public static <S, A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    Automaton<S, ? extends A> automaton) {

    return of(
      automaton,
      NumberingStateEncoderFactory.INSTANCE,
      new RangedVariableAllocator(
        ATOMIC_PROPOSITION,
        STATE,
        COLOUR,
        SUCCESSOR_STATE));
  }

  public static <S, A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    Automaton<S, ? extends A> automaton,
    StateEncoderFactory encoderFactory,
    VariableAllocator allocator) {

    List<String> atomicPropositions = List.copyOf(automaton.atomicPropositions());
    StateEncoder<S> stateEncoder = encoderFactory.create(automaton);
    VariableAllocation allocation = allocator.allocate(
      stateEncoder.stateVariables(),
      atomicPropositions.size(),
      automaton.acceptance().acceptanceSets());

    BddSetFactory factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory(allocation.variableNames());

    BddSet initialStates = factory.of();

    // Work-list algorithm.
    Deque<S> workList = new ArrayDeque<>();
    Set<S> exploredStates = new HashSet<>();

    for (S initialState : automaton.initialStates()) {
      BitSet stateEncoding = stateEncoder.encode(initialState);
      initialStates = initialStates.union(
        factory.of(allocation.localToGlobal(stateEncoding, STATE), allocation.variables(STATE)));

      workList.add(initialState);
      exploredStates.add(initialState);
    }

    BddSet transitionRelation = factory.of();

    while (!workList.isEmpty()) {
      S state = workList.remove();
      MtBdd<Edge<S>> edgeTree = automaton.edgeTree(state);

      BitSet stateEncoding = stateEncoder.encode(state);
      BddSet stateValuationSet = factory.of(
        allocation.localToGlobal(stateEncoding, STATE),
        allocation.variables(STATE));
      transitionRelation = transitionRelation.union(
        stateValuationSet.intersection(
          encodeEdgeTree(factory, edgeTree, stateEncoder, allocation)));

      for (S successor : edgeTree.flatValues(Edge::successor)) {
        if (exploredStates.add(successor)) {
          workList.add(successor);
        }
      }
    }

    return of(atomicPropositions,
      initialStates,
      transitionRelation,
      automaton.acceptance(),
      allocation);
  }

  // TODO: add memoization for ValuationTrees.
  private static <S> BddSet encodeEdgeTree(
    BddSetFactory factory,
    MtBdd<Edge<S>> edgeTree,
    StateEncoder<S> encoder,
    VariableAllocation allocation) {

    if (edgeTree instanceof MtBdd.Leaf) {
      var leaf = (MtBdd.Leaf<Edge<S>>) edgeTree;

      BddSet edges = factory.of();

      for (Edge<S> edge : leaf.value) {
        BitSet successorEncoding
          = allocation.localToGlobal(encoder.encode(edge.successor()), SUCCESSOR_STATE);
        BitSet coloursEncoding
          = allocation.localToGlobal(edge.colours().copyInto(new BitSet()), COLOUR);
        successorEncoding.or(coloursEncoding);

        BitSet mask = allocation.variables(SUCCESSOR_STATE);
        mask.or(allocation.variables(COLOUR));

        edges = edges.union(factory.of(successorEncoding, mask));
      }

      return edges;
    } else {
      var node = (MtBdd.Node<Edge<S>>) edgeTree;

      var trueEdges = encodeEdgeTree(factory, node.trueChild, encoder, allocation);
      var falseEdges = encodeEdgeTree(factory, node.falseChild, encoder, allocation);
      var atomicProposition
        = factory.of(allocation.localToGlobal(node.variable, ATOMIC_PROPOSITION));

      return trueEdges.intersection(atomicProposition).union(
        falseEdges.intersection(atomicProposition.complement()));
    }
  }

  @Memoized
  public Automaton<BitSet, A> toAutomaton() {
    Set<BitSet> initialStates = new HashSet<>();
    VariableAllocation allocation = variableAllocation();

    initialStates().forEach(allocation.variables(STATE), x -> {
      initialStates.add(allocation.globalToLocal(x, STATE));
    });

    // TODO: Use AbstractMemoizingAutomaton.EdgeTreeImplementation for faster computation.
    return new AbstractMemoizingAutomaton.EdgesImplementation<>(
      FactorySupplier.defaultSupplier().getBddSetFactory(atomicPropositions()),
      initialStates,
      acceptance()) {

      @Override
      protected Set<Edge<BitSet>> edgesImpl(BitSet state, BitSet valuation) {
        BddSetFactory factory = transitionRelation().factory();

        BddSet stateSingletonSet = factory.of(
          allocation.localToGlobal(state, STATE),
          allocation.variables(STATE));

        BddSet atomicPropositionsSingletonSet = factory.of(
          allocation.localToGlobal(valuation, ATOMIC_PROPOSITION),
          allocation.variables(ATOMIC_PROPOSITION));

        BddSet edgesSet = stateSingletonSet
          .intersection(atomicPropositionsSingletonSet)
          .intersection(transitionRelation());

        return edgesSet.toSet().stream()
          .map((BitSet edge) -> {
            assert state.equals(allocation.globalToLocal(edge, STATE));
            assert valuation.equals(allocation.globalToLocal(edge, ATOMIC_PROPOSITION));

            return Edge.of(
              allocation.globalToLocal(edge, SUCCESSOR_STATE),
              allocation.globalToLocal(edge, COLOUR));
          })
          .collect(Collectors.toUnmodifiableSet());
      }
    };
  }

  public interface StateEncoderFactory {

    <S> StateEncoder<S> create(Automaton<? extends S, ?> automaton);

  }

  public interface StateEncoder<S> {

    int stateVariables();

    BitSet encode(S state);

  }

  public interface VariableAllocator {

    VariableAllocation allocate(int stateVariables, int atomicPropositions, int colours);

  }

  public interface VariableAllocation {

    BitSet variables(VariableType type);

    List<String> variableNames();

    int localToGlobal(int variable, VariableType type);

    int globalToLocal(int variable, VariableType type);

    BitSet localToGlobal(BitSet bitSet, VariableType type);

    BitSet globalToLocal(BitSet bitSet, VariableType type);

    enum VariableType {
      STATE, COLOUR, ATOMIC_PROPOSITION, SUCCESSOR_STATE
    }
  }

}
