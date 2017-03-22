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

package owl.automaton.ldba;

import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.output.HoaConsumerExtended;
import owl.automaton.output.HoaPrintable;
import owl.collections.ValuationSet;

public final class LimitDeterministicAutomaton<S, T, U extends GeneralizedBuchiAcceptance, V>
  implements HoaPrintable {

  private final MutableAutomaton<T, U> acceptingComponent;
  private final Set<T> acceptingComponentInitialStates;
  private final Function<T, V> componentAnnotation;
  private final Set<V> components;
  private final SetMultimap<S, T> epsilonJumps;
  private final MutableAutomaton<S, NoneAcceptance> initialComponent;
  private final Table<S, ValuationSet, Set<T>> valuationSetJumps;

  LimitDeterministicAutomaton(MutableAutomaton<S, NoneAcceptance> initialComponent,
    MutableAutomaton<T, U> acceptingComponent,
    SetMultimap<S, T> epsilonJumps,
    Table<S, ValuationSet, Set<T>> valuationSetJumps,
    Set<T> acceptingComponentInitialStates,
    Set<V> component,
    Function<T, V> componentAnnotation) {
    this.initialComponent = initialComponent;
    this.acceptingComponent = acceptingComponent;
    this.epsilonJumps = epsilonJumps;
    this.valuationSetJumps = valuationSetJumps;
    this.acceptingComponentInitialStates = acceptingComponentInitialStates;
    components = component;
    this.componentAnnotation = componentAnnotation;
  }

  public MutableAutomaton<T, U> getAcceptingComponent() {
    return acceptingComponent;
  }

  public V getAnnotation(T key) {
    return componentAnnotation.apply(key);
  }

  public Set<V> getComponents() {
    return components;
  }

  public Set<T> getEpsilonJumps(S state) {
    return Collections.unmodifiableSet(epsilonJumps.get(state));
  }

  public MutableAutomaton<S, NoneAcceptance> getInitialComponent() {
    return initialComponent;
  }

  public Map<ValuationSet, Set<T>> getValuationSetJumps(S state) {
    return Collections.unmodifiableMap(valuationSetJumps.row(state));
  }

  public boolean isDeterministic() {
    return initialComponent.stateCount() == 0;
  }

  @Override
  public void setVariables(List<String> variables) {
    acceptingComponent.setVariables(variables);
  }

  public int size() {
    return initialComponent.stateCount() + acceptingComponent.stateCount();
  }

  @Override
  public void toHoa(HOAConsumer consumer, EnumSet<Option> options) {
    HoaConsumerExtended<Object> consumerExt = new HoaConsumerExtended<>(consumer,
      acceptingComponent.getVariables(),
      acceptingComponent.getAcceptance(),
      Sets.union(initialComponent.getInitialStates(), acceptingComponentInitialStates),
      size(), options, false);

    for (S state : initialComponent.getStates()) {
      consumerExt.addState(state);
      initialComponent.getLabelledEdges(state).forEach(consumerExt::addEdge);
      epsilonJumps.get(state).forEach(consumerExt::addEpsilonEdge);
      valuationSetJumps.row(state).forEach((a, b) -> b.forEach(d -> consumerExt.addEdge(a, d)));
      consumerExt.notifyEndOfState();
    }

    for (T state : acceptingComponent.getStates()) {
      consumerExt.addState(state);
      acceptingComponent.getLabelledEdges(state).forEach(consumerExt::addEdge);
      consumerExt.notifyEndOfState();
    }

    consumerExt.notifyEnd();
  }

  @Override
  public String toString() {
    try {
      return toString(EnumSet.allOf(Option.class));
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }

  public String toString(EnumSet<Option> options) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      toHoa(new HOAConsumerPrint(stream), options);
      return stream.toString("UTF8");
    }
  }
}