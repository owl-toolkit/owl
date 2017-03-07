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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.output.HoaConsumerExtended;
import owl.automaton.output.HoaPrintable;
import owl.collections.BitSets;
import owl.collections.ValuationSet;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;

public abstract class LegacyAutomaton<S extends AutomatonState<S>, AccT extends OmegaAcceptance>
  implements HoaPrintable {

  protected final Factories factories;
  protected final Map<S, Map<Edge<S>, ValuationSet>> transitions;
  protected final ValuationSetFactory valuationSetFactory;
  private final AtomicInteger atomicSize;
  protected final AccT acceptance;
  protected Set<S> initialStates;
  protected List<String> variables;

  protected LegacyAutomaton(AccT acceptance, Factories factories) {
    this(acceptance, factories, new AtomicInteger(0));
  }

  protected LegacyAutomaton(AccT acceptance, Factories factories, AtomicInteger integer) {
    this(new HashMap<>(), acceptance, factories, integer);
  }

  protected LegacyAutomaton(LegacyAutomaton<S, ?> automaton, AccT acceptance) {
    this(automaton.factories, automaton.transitions, acceptance);
  }

  protected LegacyAutomaton(Factories factories,
    Map<S, Map<Edge<S>, ValuationSet>> transitions, AccT acceptance) {
    this(transitions, acceptance, factories, new AtomicInteger());
  }

  private LegacyAutomaton(Map<S, Map<Edge<S>, ValuationSet>> transitions, AccT acceptance,
    Factories valuationSetFactory, AtomicInteger atomicSize) {
    this.transitions = transitions;
    this.acceptance = acceptance;
    this.valuationSetFactory = valuationSetFactory.valuationSetFactory;
    this.factories = valuationSetFactory;
    this.atomicSize = atomicSize;
    this.initialStates = new HashSet<>();
    this.variables = IntStream.range(0, this.valuationSetFactory.getSize())
      .mapToObj(i -> "p" + i).collect(ImmutableList.toImmutableList());
  }

  /**
   * Adds given state to the initial state set if not already present.
   *
   * @param state
   *     The state to be added to the initial state set
   */
  public final void addInitialState(S state) {
    Preconditions.checkNotNull(state);
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
    Deque<S> workDeque = new ArrayDeque<>(getInitialStates());

    workDeque.removeIf(transitions::containsKey);

    // Return if already generated
    if (workDeque.isEmpty()) {
      return;
    }

    atomicSize.set(size() + workDeque.size());
    Set<S> seenStates = new HashSet<>(getInitialStates());

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

  public AccT getAcceptance() {
    return acceptance;
  }

  public Factories getFactories() {
    return factories;
  }

  public ValuationSetFactory getFactory() {
    return factories.valuationSetFactory;
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
    return Iterables.getOnlyElement(getInitialStates());
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

      for (BitSet valuation : BitSets.powerSet(sensitiveAlphabet)) {
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

  public List<String> getVariables() {
    return variables;
  }

  public boolean hasSuccessors(S state) {
    return !getSuccessors(state).isEmpty();
  }

  /**
   * This method has no side effects.
   *
   * @param scc:
   *     set of states
   *
   * @return true if the only transitions from scc go to scc again and false otherwise
   */
  public boolean isBscc(Set<S> scc) {
    for (S s : scc) {
      for (Edge<S> edge : getSuccessors(s).keySet()) {
        if (!scc.contains(edge.getSuccessor())) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean isDeadState(S state, Map<Edge<S>, ValuationSet> successors) {
    // The state has no successors, thus it is dead.
    if (successors.isEmpty()) {
      return true;
    }

    if (acceptance instanceof GeneralizedBuchiAcceptance) {
      GeneralizedBuchiAcceptance acceptance = (GeneralizedBuchiAcceptance) this.acceptance;

      BitSet requiredSets = new BitSet();
      requiredSets.set(0, acceptance.getAcceptanceSets());

      successors.keySet().forEach(edge -> {
        if (!Objects.equals(edge.getSuccessor(), state)) {
          requiredSets.clear();
        }
        edge.acceptanceSetIterator().forEachRemaining((IntConsumer) requiredSets::clear);
      });

      if (!requiredSets.isEmpty()) {
        return true;
      }
    }

    return false;
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
      .allMatch(edge -> Objects.equals(state, edge.getSuccessor()));
  }

  public boolean isTransient(S state) {
    return getSuccessors(state).keySet().stream()
      .noneMatch(edge -> Objects.equals(state, edge.getSuccessor()));
  }

  /**
   * Remove states from the automaton, that are unreachable from the set of protected states or
   * that cannot belong to an infinite accepting path.
   *
   * @param protectedStates
   *     the set of states that are the initial states for a reachability analysis
   *
   * @return states that cannot belong to an infinite path
   */
  public Set<S> removeDeadStates(Set<S> protectedStates) {
    Set<S> deadStates = new HashSet<>();

    Set<S> reachableStates = new HashSet<>(protectedStates);
    Deque<S> workDeque = new ArrayDeque<>(protectedStates);

    while (!workDeque.isEmpty()) {
      S current = workDeque.remove();

      Map<Edge<S>, ValuationSet> successors = getSuccessors(current);

      if (isDeadState(current, successors) && !protectedStates.contains(current)) {
        deadStates.add(current);
        continue;
      }

      successors.forEach((edge, v) -> {
        if (reachableStates.add(edge.getSuccessor())) {
          workDeque.add(edge.getSuccessor());
        }
      });
    }

    // Remove dead and unreachable states
    removeStatesIf(s -> deadStates.contains(s) || !reachableStates.contains(s));

    // Fix-point iteration. (Inefficient...)
    int oldSize;

    do {
      oldSize = size();

      transitions.forEach((state, successors) -> {
        if (isDeadState(state, successors) && !protectedStates.contains(state)) {
          deadStates.add(state);
        }
      });

      removeStatesIf(deadStates::contains);
    }
    while (oldSize > size());

    return deadStates;
  }

  /**
   * Removes given state from the initial state set, if present.
   *
   * @param state
   *     The state to be removed from the initial state set.
   */
  public void removeInitialState(S state) {
    Preconditions.checkNotNull(state);
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
      transitions.forEach((state, successors) -> successors.keySet()
        .removeIf(edge -> predicate.test(edge.getSuccessor())));
    }

    setInitialStates(initialStates);
  }

  /**
   * Sets the unique initial state set. This is equivalent to calling
   * {@link #setInitialStates(Set)} with {@code ImmutableSet.of(state)}.
   *
   * @param state
   *     The new initial state.
   */
  public void setInitialState(S state) {
    this.initialStates = ImmutableSet.of(Preconditions.checkNotNull(state));
  }

  public void setInitialStates(Set<? extends S> states) {
    this.initialStates = ImmutableSet.copyOf(states);
  }

  @Override
  public void setVariables(List<String> variables) {
    this.variables = ImmutableList.copyOf(variables);
    factories.equivalenceClassFactory.setVariables(this.variables);
  }

  public int size() {
    return transitions.size();
  }

  @Override
  public void toHoa(HOAConsumer consumer, EnumSet<Option> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer,
      variables, acceptance, initialStates, size(), options);
    toHoaBody(hoa);
    hoa.notifyEnd();
  }

  public final void toHoaBody(HoaConsumerExtended<S> hoa) {
    getStates().forEach(s -> {
      hoa.addState(s);
      getSuccessors(s).forEach(hoa::addEdge);
      toHoaBodyEdge(s, hoa);
      hoa.notifyEndOfState();
    });
  }

  /**
   * Override this method, if you want output additional edges for {@param state} not present in
   * {@link LegacyAutomaton#transitions}.
   */
  @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
  protected void toHoaBodyEdge(S state, HoaConsumerExtended<S> hoa) {
    // To be overridden
  }

  @Override
  public String toString() {
    try (OutputStream stream = new ByteArrayOutputStream()) {
      toHoa(new HOAConsumerPrint(stream), EnumSet.of(Option.ANNOTATIONS));
      return stream.toString();
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }

  public void updateEdge(S state, S successor, BitSet acceptance) {
    Map<Edge<S>, ValuationSet> successors = transitions.get(state);

    if (successors == null) {
      return;
    }

    ValuationSet valuationSet = factories.valuationSetFactory.createEmptyValuationSet();

    successors.entrySet().removeIf((entry) -> {
      if (Objects.equals(entry.getKey().getSuccessor(), successor)) {
        valuationSet.addAll(entry.getValue());
        return true;
      }

      return false;
    });

    if (!valuationSet.isEmpty()) {
      successors.put(Edges.create(successor, acceptance), valuationSet);
    }
  }
}
