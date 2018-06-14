/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.automaton.ldba;

import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.collections.ValuationSet;

public final class LimitDeterministicAutomatonImpl<S, T, U extends GeneralizedBuchiAcceptance, V>
  implements LimitDeterministicAutomaton<S, T, U, V> {

  private final MutableAutomaton<T, U> acceptingComponent;
  private final Set<T> acceptingComponentInitialStates;
  private final Function<T, V> componentAnnotation;
  private final Set<V> components;
  private final SetMultimap<S, T> epsilonJumps;
  private final MutableAutomaton<S, NoneAcceptance> initialComponent;
  private final Table<S, ValuationSet, Set<T>> valuationSetJumps;

  public LimitDeterministicAutomatonImpl(MutableAutomaton<S, NoneAcceptance> initialComponent,
    MutableAutomaton<T, U> acceptingComponent,
    SetMultimap<S, T> epsilonJumps,
    Table<S, ValuationSet, Set<T>> valuationSetJumps,
    Set<T> acceptingComponentInitialStates) {
    this(initialComponent, acceptingComponent, epsilonJumps, valuationSetJumps, Set.of(), x -> null,
      acceptingComponentInitialStates);
  }

  LimitDeterministicAutomatonImpl(MutableAutomaton<S, NoneAcceptance> initialComponent,
    MutableAutomaton<T, U> acceptingComponent,
    SetMultimap<S, T> epsilonJumps,
    Table<S, ValuationSet, Set<T>> valuationSetJumps,
    Set<V> component,
    Function<T, V> componentAnnotation,
    Set<T> acceptingComponentInitialStates) {
    this.acceptingComponent = acceptingComponent;
    this.acceptingComponentInitialStates = Set.copyOf(acceptingComponentInitialStates);
    this.componentAnnotation = componentAnnotation;
    this.components = component;
    this.epsilonJumps = epsilonJumps;
    this.initialComponent = initialComponent;
    this.valuationSetJumps = valuationSetJumps;

    assert this.acceptingComponent.states().containsAll(this.acceptingComponentInitialStates);
    assert this.acceptingComponent.is(Automaton.Property.SEMI_DETERMINISTIC);
  }

  @Override
  public Set<Object> initialStates() {
    return Sets.union(initialComponent.initialStates(), acceptingComponentInitialStates);
  }

  @Override
  public Automaton<T, U> acceptingComponent() {
    return acceptingComponent;
  }

  @Override
  public V annotation(T key) {
    return componentAnnotation.apply(key);
  }

  @Override
  public Set<V> components() {
    return components;
  }

  @Override
  public Set<T> epsilonJumps(S state) {
    return Collections.unmodifiableSet(epsilonJumps.get(state));
  }

  @Override
  public Automaton<S, NoneAcceptance> initialComponent() {
    return initialComponent;
  }

  @Override
  public Map<ValuationSet, Set<T>> valuationSetJumps(S state) {
    return Collections.unmodifiableMap(valuationSetJumps.row(state));
  }

  public List<String> variables() {
    return acceptingComponent.factory().alphabet();
  }
}