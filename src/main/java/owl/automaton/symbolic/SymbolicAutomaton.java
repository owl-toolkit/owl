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

package owl.automaton.symbolic;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.SUCCESSOR_STATE;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Streams;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
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
import owl.collections.ImmutableBitSet;

/**
 * An automaton over infinite words.
 *
 * <p>This class provides a symbolic automaton representation.
 *
 * @param <A>
 *   the acceptance class
 */
@AutoValue
public abstract class SymbolicAutomaton<A extends EmersonLeiAcceptance> {

  public abstract List<String> atomicPropositions();

  public abstract BddSet initialStates();

  public abstract BddSet transitionRelation();

  public abstract A acceptance();

  public abstract VariableAllocation variableAllocation();

  public abstract Set<Automaton.Property> properties();

  public abstract int colourOffset();

  public BddSet successors(BddSet statesAndValuation) {
    checkArgument(statesAndValuation.factory() == factory());
    ImmutableBitSet quantifyOver = variableAllocation().variables(STATE, ATOMIC_PROPOSITION);
    ImmutableBitSet states = variableAllocation().variables(STATE);
    ImmutableBitSet successorStates = variableAllocation().variables(SUCCESSOR_STATE);
    return transitionRelation()
      .intersection(statesAndValuation
        .project(variableAllocation().variables(COLOUR))
      )
      .project(quantifyOver)
      .relabel(variable -> {
        if (states.contains(variable)) {
          return variableAllocation()
            .localToGlobal(variableAllocation().globalToLocal(variable, STATE), SUCCESSOR_STATE);
        } else if (successorStates.contains(variable)) {
          return variableAllocation()
            .localToGlobal(variableAllocation().globalToLocal(variable, SUCCESSOR_STATE), STATE);
        } else {
          return variable;
        }
      });
  }

  public BddSet predecessors(BddSet statesAndValuation) {
    checkArgument(statesAndValuation.factory() == factory());
    ImmutableBitSet quantifyOver = variableAllocation().variables(
      SUCCESSOR_STATE,
      ATOMIC_PROPOSITION,
      COLOUR
    );
    ImmutableBitSet successorStates = variableAllocation().variables(SUCCESSOR_STATE);
    ImmutableBitSet states = variableAllocation().variables(STATE);
    return transitionRelation()
      .intersection(statesAndValuation
        .relabel(variable -> {
          if (successorStates.contains(variable)) {
            return variableAllocation()
              .localToGlobal(variableAllocation().globalToLocal(variable, SUCCESSOR_STATE), STATE);
          } else if (states.contains(variable)) {
            return variableAllocation()
              .localToGlobal(variableAllocation().globalToLocal(variable, STATE), SUCCESSOR_STATE);
          } else {
            return variable;
          }
        })
      )
      .project(quantifyOver);
  }

  @Memoized
  public BddSet reachableStates() {
    BddSet previousStates = initialStates().factory().of(false);
    BddSet currentStates = initialStates().intersection(
      initialStates().factory().of(new BitSet(),
        variableAllocation().variables(COLOUR).copyInto(new BitSet()))
    );
    while (!previousStates.equals(currentStates)) {
      previousStates = currentStates;
      currentStates = currentStates.union(successors(currentStates));
    }
    return currentStates;
  }

  public boolean is(Automaton.Property property) {
    return properties().contains(property);
  }

  public BddSetFactory factory() {
    return transitionRelation().factory();
  }

  /**
   * Package-private constructor that is only available to trusted implementations.
   */
  static <A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    List<String> atomicPropositions,
    BddSet initialStates,
    BddSet transitionRelation,
    A acceptance,
    VariableAllocation variableAllocation,
    Set<Automaton.Property> properties) {

    checkArgument(initialStates.factory().equals(transitionRelation.factory()));

    return of(
      List.copyOf(atomicPropositions),
      initialStates,
      transitionRelation,
      acceptance,
      variableAllocation,
      Set.copyOf(properties),
      0
    );
  }

  static <A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    List<String> atomicPropositions,
    BddSet initialStates,
    BddSet transitionRelation,
    A acceptance,
    VariableAllocation variableAllocation,
    Set<Automaton.Property> properties,
    int colourOffset
  ) {
    return new AutoValue_SymbolicAutomaton<>(
      List.copyOf(atomicPropositions),
      initialStates,
      transitionRelation,
      acceptance,
      variableAllocation,
      Set.copyOf(properties),
      colourOffset
    );
  }

  public static <S, A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    Automaton<S, ? extends A> automaton
  ) {
    return of(
      automaton,
      FactorySupplier.defaultSupplier().getBddSetFactory(),
      automaton.atomicPropositions()
    );
  }

  public static <S, A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    Automaton<S, ? extends A> automaton, BddSetFactory factory, List<String> atomicPropositions) {

    checkArgument(
      Collections.indexOfSubList(atomicPropositions, automaton.atomicPropositions()) == 0);

    return of(
      automaton,
      atomicPropositions,
      factory,
      NumberingStateEncoderFactory.INSTANCE,
      new RangedVariableAllocator(
        ATOMIC_PROPOSITION,
        STATE,
        COLOUR,
        SUCCESSOR_STATE));
  }

  private static <S, A extends EmersonLeiAcceptance> SymbolicAutomaton<A> of(
    Automaton<S, ? extends A> automaton,
    List<String> atomicPropositions,
    BddSetFactory factory,
    StateEncoderFactory encoderFactory,
    VariableAllocator allocator) {

    List<String> atomicPropositionsCopy = List.copyOf(atomicPropositions);
    StateEncoder<S> stateEncoder = encoderFactory.create(automaton);
    VariableAllocation allocation = allocator.allocate(
      stateEncoder.stateVariables(),
      atomicPropositionsCopy.size(),
      automaton.acceptance().acceptanceSets());

    BddSet initialStates = factory.of(false);

    // Work-list algorithm.
    Deque<S> workList = new ArrayDeque<>();
    Set<S> exploredStates = new HashSet<>();

    for (S initialState : automaton.initialStates()) {
      BitSet stateEncoding = stateEncoder.encode(initialState);
      initialStates = initialStates.union(
        factory.of(allocation.localToGlobal(stateEncoding, STATE),
          allocation.variables(STATE).copyInto(new BitSet())));

      workList.add(initialState);
      exploredStates.add(initialState);
    }

    BddSet transitionRelation = factory.of(false);

    while (!workList.isEmpty()) {
      S state = workList.remove();
      MtBdd<Edge<S>> edgeTree = automaton.edgeTree(state);

      BitSet stateEncoding = stateEncoder.encode(state);
      BddSet stateValuationSet = factory.of(
        allocation.localToGlobal(stateEncoding, STATE),
        allocation.variables(STATE).copyInto(new BitSet()));
      transitionRelation = transitionRelation.union(
        stateValuationSet.intersection(
          encodeEdgeTree(factory, edgeTree, stateEncoder, allocation)));

      for (Edge<S> edge : edgeTree.flatValues()) {
        if (exploredStates.add(edge.successor())) {
          workList.add(edge.successor());
        }
      }
    }

    var properties = Arrays.stream(Automaton.Property.values())
      .filter(automaton::is)
      .collect(Collectors.toUnmodifiableSet());

    return of(atomicPropositionsCopy,
      initialStates,
      transitionRelation,
      automaton.acceptance(),
      allocation,
      properties);
  }

  // TODO: add memoization for ValuationTrees.
  private static <S> BddSet encodeEdgeTree(
    BddSetFactory factory,
    MtBdd<Edge<S>> edgeTree,
    StateEncoder<S> encoder,
    VariableAllocation allocation) {

    if (edgeTree instanceof MtBdd.Leaf<Edge<S>> leaf) {
      BddSet edges = factory.of(false);

      for (Edge<S> edge : leaf.value) {
        BitSet successorEncoding
          = allocation.localToGlobal(encoder.encode(edge.successor()), SUCCESSOR_STATE);
        BitSet coloursEncoding
          = allocation.localToGlobal(edge.colours().copyInto(new BitSet()), COLOUR);
        successorEncoding.or(coloursEncoding);

        ImmutableBitSet mask = allocation.variables(SUCCESSOR_STATE, COLOUR);
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
  public Automaton<ImmutableBitSet, A> toAutomaton() {

    VariableAllocation allocation = variableAllocation();
    Set<ImmutableBitSet> initialStates = Streams
      .stream(initialStates().iterator(allocation.variables(STATE)))
      .map(x -> allocation.globalToLocal(x, STATE))
      .collect(Collectors.toUnmodifiableSet());

    // TODO: Use AbstractMemoizingAutomaton.EdgeTreeImplementation for faster computation.
    return new AbstractMemoizingAutomaton.EdgesImplementation<>(
      atomicPropositions(),
      factory(),
      initialStates,
      acceptance()) {

      @Override
      protected Set<Edge<ImmutableBitSet>> edgesImpl(ImmutableBitSet state, BitSet valuation) {
        BddSet stateSingletonSet = factory.of(
          allocation.localToGlobal(state.copyInto(new BitSet()), STATE),
          allocation.variables(STATE).copyInto(new BitSet()));

        BddSet atomicPropositionsSingletonSet = factory.of(
          allocation.localToGlobal(valuation, ATOMIC_PROPOSITION),
          allocation.variables(ATOMIC_PROPOSITION).copyInto(new BitSet()));

        BddSet edgesSet = stateSingletonSet
          .intersection(atomicPropositionsSingletonSet)
          .intersection(transitionRelation());

        // TODO: replace this rough over-approximation by a more precise check.
        return Streams.stream(edgesSet.iterator(allocation.numberOfVariables()))
          .map((BitSet edge) -> {
            assert state.equals(allocation.globalToLocal(edge, STATE));
            // assert valuation.equals(allocation.globalToLocal(edge, ATOMIC_PROPOSITION));

            return Edge.of(
              allocation.globalToLocal(edge, SUCCESSOR_STATE),
              allocation.globalToLocal(edge, COLOUR).copyInto(new BitSet())
                .get(colourOffset(), colourOffset() + acceptance().acceptanceSets()));
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

  enum VariableType {
    STATE, COLOUR, ATOMIC_PROPOSITION, SUCCESSOR_STATE
  }

  public interface VariableAllocation {

    ImmutableBitSet variables(VariableType... type);

    default int numberOfVariables() {
      return variables(VariableType.values()).size();
    }

    default VariableType typeOf(int variable) {
      for (var type : VariableType.values()) {
        if (variables(type).contains(variable)) {
          return type;
        }
      }
      throw new IllegalArgumentException(variable + " is not a variable!");
    }

    List<String> variableNames();

    int localToGlobal(int variable, VariableType type);

    int globalToLocal(int variable, VariableType type);

    default BitSet localToGlobal(BitSet bitSet, VariableType type) {
      BitSet result = new BitSet();
      for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
        result.set(localToGlobal(i, type));
      }
      return result;
    }

    default ImmutableBitSet globalToLocal(BitSet bitSet, VariableType type) {
      BitSet result = new BitSet();
      ImmutableBitSet variables = variables(type);
      for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
        if (variables.contains(i)) {
          result.set(globalToLocal(i, type));
        }
      }
      return ImmutableBitSet.copyOf(result);
    }
  }

  interface AllocationCombiner extends VariableAllocation {

    int localToGlobal(int variable, VariableAllocation allocation);

    int globalToLocal(int variable, VariableAllocation allocation);

    default BitSet localToGlobal(BitSet bitSet, VariableAllocation allocation) {
      BitSet result = new BitSet();
      for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
        result.set(localToGlobal(i, allocation));
      }
      return result;
    }

    default BitSet globalToLocal(BitSet bitSet, VariableAllocation allocation) {
      BitSet result = new BitSet();
      for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
        result.set(globalToLocal(i, allocation));
      }
      return result;
    }
  }
}
