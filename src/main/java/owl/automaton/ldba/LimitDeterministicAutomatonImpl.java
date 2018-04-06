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
import com.google.common.collect.Table;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
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
  private final Function<T, V> componentAnnotation;
  private final Set<V> components;
  private final SetMultimap<S, T> epsilonJumps;
  private final MutableAutomaton<S, NoneAcceptance> initialComponent;
  private final Table<S, ValuationSet, Set<T>> valuationSetJumps;

  public LimitDeterministicAutomatonImpl(MutableAutomaton<S, NoneAcceptance> initialComponent,
    MutableAutomaton<T, U> acceptingComponent,
    SetMultimap<S, T> epsilonJumps,
    Table<S, ValuationSet, Set<T>> valuationSetJumps,
    Set<V> component,
    Function<T, V> componentAnnotation) {
    this.initialComponent = initialComponent;
    this.acceptingComponent = acceptingComponent;
    this.epsilonJumps = epsilonJumps;
    this.valuationSetJumps = valuationSetJumps;
    components = component;
    this.componentAnnotation = componentAnnotation;
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

  @Override
  public List<String> variables() {
    return acceptingComponent.variables();
  }

  @Override
  public String toString() {
    try {
      return toString(EnumSet.allOf(HoaOption.class));
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }
}