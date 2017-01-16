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

package omega_automaton;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import omega_automaton.output.HOAConsumerExtended;
import omega_automaton.output.HOAPrintable;
import owl.automaton.edge.Edge;

public abstract class Automaton<S extends AutomatonState<S>, Acc extends OmegaAcceptance>
  implements HOAPrintable {

  protected final Map<S, Map<Edge<S>, ValuationSet>> transitions;
  protected final ValuationSetFactory valuationSetFactory;
  private final AtomicInteger atomicSize;
  protected Acc acceptance;
  protected Map<Integer, String> atomMapping;
  private Set<S> initialStates;

  protected Automaton(Acc acceptance, ValuationSetFactory factory) {
    this(acceptance, factory, new AtomicInteger(0));
  }

  protected Automaton(Acc acceptance, ValuationSetFactory factory, AtomicInteger integer) {
    this(new HashMap<>(), acceptance, factory, integer);
  }

  protected Automaton(Automaton<S, ?> automaton, Acc acceptance) {
    this(automaton.valuationSetFactory, automaton.transitions, acceptance);
  }

  protected Automaton(ValuationSetFactory valuationSetFactory,
    Map<S, Map<Edge<S>, ValuationSet>> transitions, Acc acceptance) {
    this(transitions, acceptance, valuationSetFactory, new AtomicInteger());
  }

  private Automaton(Map<S, Map<Edge<S>, ValuationSet>> transitions, Acc acceptance,
    ValuationSetFactory valuationSetFactory, AtomicInteger atomicSize) {
    this.transitions = transitions;
    this.acceptance = acceptance;
    this.valuationSetFactory = valuationSetFactory;
    this.atomicSize = atomicSize;
    this.atomMapping = new HashMap<>();
    this.initialStates = new HashSet<>();
    IntStream.range(0, valuationSetFactory.getSize()).forEach(i -> atomMapping.put(i, "p" + i));
  }

  /**
   * Adds given state to the initial state set if not already present.
   *
   * @param state
   *     The state to be added to the initial state set
   */
  public final void addInitialState(S state) {
    checkNotNull(state);
    if (this.initialStates instanceof ImmutableSet) {
      this.initialStates = new HashSet<>(this.initialStates);
    }
    this.initialStates.add(state);
  }

  public void complete() {
    S trapState = generateRejectingTrap();
    Edge<S> rejectingEdge = generateRejectingEdge(trapState);
    boolean usedTrapState = false;

    // Add missing edges to trap state.
    for (Map<Edge<S>, ValuationSet> successors : transitions.values()) {
      ValuationSet set = valuationSetFactory.createEmptyValuationSet();
      successors.values().forEach(set::addAll);
      ValuationSet complementSet = set.complement();

      if (!complementSet.isEmpty()) {
        successors.put(rejectingEdge, complementSet);
        usedTrapState = true;
      }

      set.free();
    }

    if (initialStates.isEmpty() || transitions.isEmpty()) {
      usedTrapState = true;
      initialStates = Collections.singleton(trapState);
    }

    // Add trap state to the transitions table, only if it was used.
    if (usedTrapState) {
      transitions.put(trapState,
        Collections.singletonMap(rejectingEdge, valuationSetFactory.createUniverseValuationSet()));
    }
  }

  public void generate() {
    Collection<S> seenStates = new HashSet<>(getInitialStates());
    Deque<S> workDeque = new ArrayDeque<>(getInitialStates());

    workDeque.removeIf(transitions::containsKey);
    atomicSize.set(size() + workDeque.size());

    // Return if already generated
    if (workDeque.isEmpty()) {
      return;
    }

    while (!workDeque.isEmpty()) {
      S current = workDeque.removeLast();

      for (Edge<S> successor : getSuccessors(current).keySet()) {
        if (!transitions.containsKey(successor.getSuccessor()) && seenStates
          .add(successor.getSuccessor())) {
          workDeque.add(successor.getSuccessor());
          atomicSize.getAndIncrement();
        }
      }

      // Generating the automaton is a long-running task. If the thread gets interrupted, we
      // just cancel everything. Warning: All data structures are now inconsistent!
      if (Thread.interrupted()) {
        throw new CancellationException();
      }
    }

    atomicSize.set(size());
  }

  @Nonnull
  protected Edge<S> generateRejectingEdge(S successor) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  protected S generateRejectingTrap() {
    throw new UnsupportedOperationException();
  }

  public Acc getAcceptance() {
    return acceptance;
  }

  public Map<Integer, String> getAtomMapping() {
    return atomMapping;
  }

  public ValuationSetFactory getFactory() {
    return valuationSetFactory;
  }

  /**
   * Returns the initial state if there is a unique one. Otherwise an
   * {@code IllegalStateException} is thrown.
   *
   * @return The unique initial state if it exists.
   *
   * @throws IllegalStateException
   *     If there are zero or multiple initial states.
   */
  @Nonnull
  public S getInitialState() {
    Set<S> initialStates = getInitialStates();
    checkState(initialStates.size() == 1);
    return initialStates.iterator().next();
  }

  /**
   * Returns an immutable copy of the current initial state set.
   *
   * @return The current initial states.
   */
  @Nonnull
  public ImmutableSet<S> getInitialStates() {
    return ImmutableSet.copyOf(initialStates);
  }

  private void getReachableStates(Set<S> states) {
    Deque<S> workDeque = new ArrayDeque<>(states);

    while (!workDeque.isEmpty()) {
      S state = workDeque.remove();

      getSuccessors(state).forEach((edge, v) -> {
        if (states.add(edge.getSuccessor())) {
          workDeque.add(edge.getSuccessor());
        }
      });
    }
  }

  @Nonnull
  public Set<S> getStates() {
    return transitions.keySet();
  }

  @Nullable
  public Edge<S> getSuccessor(S state, BitSet valuation) {
    for (Map.Entry<Edge<S>, ValuationSet> transition : getSuccessors(state).entrySet()) {
      if (transition.getValue().contains(valuation)) {
        return transition.getKey();
      }
    }

    return null;
  }

  public Set<S> getSuccessors(S state, BitSet valuation) {
    Set<S> successors = new HashSet<>();

    for (Map.Entry<Edge<S>, ValuationSet> transition : getSuccessors(state).entrySet()) {
      if (transition.getValue().contains(valuation)) {
        successors.add(transition.getKey().getSuccessor());
      }
    }

    return successors;
  }

  public Map<Edge<S>, ValuationSet> getSuccessors(S state) {
    Map<Edge<S>, ValuationSet> successors = transitions.get(state);

    if (successors == null) {
      BitSet sensitiveAlphabet = state.getSensitiveAlphabet();
      successors = new LinkedHashMap<>();

      for (BitSet valuation : Collections3.powerSet(sensitiveAlphabet)) {
        Edge<S> successor = state.getSuccessor(valuation);

        if (successor == null) {
          continue;
        }

        ValuationSet oldVs = successors.get(successor);
        ValuationSet newVs = valuationSetFactory.createValuationSet(valuation, sensitiveAlphabet);

        if (oldVs == null) {
          successors.put(successor, newVs);
        } else {
          oldVs.addAllWith(newVs);
        }
      }

      transitions.put(state, successors);
    }

    return successors;
  }

  public boolean hasSuccessors(S state) {
    return !getSuccessors(state).isEmpty();
  }

  /**
   * This method has no side effects
   *
   * @param scc:
   *     set of states
   *
   * @return true if the only transitions from scc go to scc again and false otherwise
   */
  public boolean isBSCC(Set<S> scc) {
    for (S s : scc) {
      for (Edge<S> edge : getSuccessors(s).keySet()) {
        if (!scc.contains(edge.getSuccessor())) {
          return false;
        }
      }
    }

    return true;
  }

  public boolean isDeterministic() {
    return getStates().stream().allMatch(this::isDeterministic);
  }

  private boolean isDeterministic(S state) {
    ValuationSet valuationSet = valuationSetFactory.createEmptyValuationSet();

    for (Map.Entry<Edge<S>, ValuationSet> entry : getSuccessors(state).entrySet()) {
      if (valuationSet.intersects(entry.getValue())) {
        valuationSet.free();
        return false;
      } else {
        valuationSet.addAll(entry.getValue());
      }
    }

    valuationSet.free();
    return true;
  }

  public boolean isSink(S state) {
    return getSuccessors(state).keySet().stream()
      .allMatch(edge -> state.equals(edge.getSuccessor()));
  }

  public boolean isTransient(S state) {
    return getSuccessors(state).keySet().stream()
      .noneMatch(edge -> state.equals(edge.getSuccessor()));
  }

  /**
   * Removes given state from the initial state set, if present.
   *
   * @param state
   *     The state to be removed from the initial state set.
   */
  public void removeInitialState(S state) {
    checkNotNull(state);
    if (this.initialStates instanceof ImmutableSet) {
      this.initialStates = new HashSet<>(this.initialStates);
    }
    this.initialStates.remove(state);
  }

  public void removeStatesIf(Predicate<S> predicate) {
    Set<S> initialStates = new HashSet<>(getInitialStates());
    initialStates.removeIf(predicate);

    if (initialStates.isEmpty()) {
      transitions.clear();
    } else {
      transitions.keySet().removeIf(predicate);
      transitions.forEach((k, v) -> v.keySet().removeIf(t -> predicate.test(t.getSuccessor())));
    }
    setInitialStates(initialStates);
  }

  public void removeUnreachableStates() {
    removeUnreachableStates(getInitialStates());
  }

  public void removeUnreachableStates(Set<S> reach) {
    getReachableStates(new HashSet<>(reach));
    removeStatesIf(s -> !reach.contains(s));
  }

  public void setAtomMapping(Map<Integer, String> mapping) {
    atomMapping = new HashMap<>(mapping);
  }

  /**
   * Sets the unique initial state set. This is equivalent to calling
   * {@link #setInitialStates(Set)} with {@code ImmutableSet.of(state)}.
   *
   * @param state
   *     The new initial state.
   */
  public void setInitialState(S state) {
    this.initialStates = ImmutableSet.of(checkNotNull(state));
  }

  public void setInitialStates(Set<? extends S> states) {
    this.initialStates = ImmutableSet.copyOf(states);
  }

  public int size() {
    return transitions.size();
  }

  @Override
  public void toHOA(HOAConsumer consumer, EnumSet<Option> options) {
    HOAConsumerExtended hoa = new HOAConsumerExtended(consumer, valuationSetFactory.getSize(),
      atomMapping, acceptance, initialStates, size(), options);
    toHOABody(hoa);
    hoa.done();
  }

  public final void toHOABody(HOAConsumerExtended hoa) {
    getStates().forEach(s -> {
      hoa.addState(s);
      toHOABodyEdge(s, hoa);
      hoa.stateDone();
    });
  }

  /**
   * Override this method, if you want output additional edges for {@param state} not present in
   * {@link Automaton#transitions}.
   */
  protected void toHOABodyEdge(S state, HOAConsumerExtended hoa) {
    getSuccessors(state).forEach((edge, valuationSet) -> hoa
      .addEdge(valuationSet, edge.getSuccessor(), edge.acceptanceSetStream()));
  }

  @Override
  public String toString() {
    try (OutputStream stream = new ByteArrayOutputStream()) {
      toHOA(new HOAConsumerPrint(stream), EnumSet.of(Option.ANNOTATIONS));
      return stream.toString();
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }
}
