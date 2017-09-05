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

package owl.translations.frequency;

import static owl.automaton.output.HoaPrinter.HoaOption;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import de.tum.in.naturals.bitset.BitSets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;

public abstract class Automaton<S extends AutomatonState<S>, Acc extends OmegaAcceptance> {
  protected final Factories factories;
  protected final Map<S, Map<Edge<S>, ValuationSet>> transitions;
  protected final ValuationSetFactory vsFactory;
  private final AtomicInteger atomicSize;
  protected Acc acceptance;
  protected ImmutableList<String> variables;
  protected Set<S> initialStates;

  protected Automaton(Acc acceptance, Factories factories) {
    this(acceptance, factories, new AtomicInteger(0));
  }

  protected Automaton(Acc acceptance, Factories factories, AtomicInteger integer) {
    this(new HashMap<>(), acceptance, factories, integer);
  }

  protected Automaton(Factories factories,
    Map<S, Map<Edge<S>, ValuationSet>> transitions, Acc acceptance) {
    this(transitions, acceptance, factories, new AtomicInteger());
  }

  private Automaton(Map<S, Map<Edge<S>, ValuationSet>> transitions, Acc acceptance,
    Factories factories, AtomicInteger atomicSize) {
    this.transitions = transitions;
    this.acceptance = acceptance;
    this.vsFactory = factories.vsFactory;
    this.factories = factories;
    this.atomicSize = atomicSize;
    this.initialStates = new HashSet<>();
    this.variables = IntStream.range(0, vsFactory.alphabetSize()).mapToObj(i -> "p" + i).collect(
      ImmutableList.toImmutableList());
  }

  public void generate() {
    Collection<S> seenStates = new HashSet<>(getInitialStates());
    Deque<S> workDeque = new ArrayDeque<>(seenStates);
    workDeque.removeIf(transitions::containsKey);
    atomicSize.set(size() + workDeque.size());

    // Return if already generated
    if (workDeque.isEmpty()) {
      return;
    }

    while (!workDeque.isEmpty()) {
      S current = workDeque.removeLast();

      for (Edge<S> successor : getSuccessors(current).keySet()) {
        if (!transitions.containsKey(successor.successor()) && seenStates
          .add(successor.successor())) {
          workDeque.add(successor.successor());
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

  public Acc getAcceptance() {
    return acceptance;
  }

  public ImmutableList<String> getVariables() {
    return variables;
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
        ValuationSet newVs = vsFactory.of(valuation, sensitiveAlphabet);

        if (oldVs == null) {
          successors.put(successor, newVs);
        } else {
          oldVs = vsFactory.union(oldVs, newVs);
        }
      }

      transitions.put(state, successors);
    }

    return successors;
  }

  /**
   * Sets the unique initial state set.
   *
   * @param state
   *     The new initial state.
   */
  public void setInitialState(S state) {
    this.initialStates = ImmutableSet.of(Preconditions.checkNotNull(state));
  }

  public int size() {
    return transitions.size();
  }

  public void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended hoa = new HoaConsumerExtended(consumer, vsFactory.alphabetSize(),
      variables, acceptance, initialStates, size(), options);
    toHoaBody(hoa);
    hoa.notifyEnd();
  }

  public final void toHoaBody(HoaConsumerExtended hoa) {
    getStates().forEach(s -> {
      hoa.addState(s);
      getSuccessors(s).forEach(hoa::addEdge);
      toHoaBodyEdge(s, hoa);
      hoa.notifyEndOfState();
    });
  }

  /**
   * Override this method, if you want output additional edges for {@code state} not present in
   * {@link Automaton#transitions}.
   */
  protected abstract void toHoaBodyEdge(S state, HoaConsumerExtended hoa);

  @Override
  public String toString() {
    try (OutputStream stream = new ByteArrayOutputStream()) {
      toHoa(new HOAConsumerPrint(stream), EnumSet.of(HoaOption.ANNOTATIONS));
      return stream.toString();
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }
}
