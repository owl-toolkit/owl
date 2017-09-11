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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.transformations.PowerSetAutomaton;
import owl.collections.ValuationSet;

public class CutDeterministicAutomaton<S, T, U extends GeneralizedBuchiAcceptance, V>
  implements LimitDeterministicAutomaton<Set<S>, T, U, V> {

  private final LimitDeterministicAutomaton<S, T, U, V> ldba;

  CutDeterministicAutomaton(LimitDeterministicAutomaton<S, T, U, V> ldba) {
    this.ldba = ldba;
  }

  @Override
  public Automaton<T, U> getAcceptingComponent() {
    return ldba.getAcceptingComponent();
  }

  @Override
  public V getAnnotation(T key) {
    return ldba.getAnnotation(key);
  }

  @Override
  public Set<V> getComponents() {
    return ldba.getComponents();
  }

  @Override
  public Set<T> getEpsilonJumps(Set<S> state) {
    ImmutableSet.Builder<T> builder = ImmutableSet.builder();
    state.forEach(s -> builder.addAll(ldba.getEpsilonJumps(s)));
    return builder.build();
  }

  @Override
  public Automaton<Set<S>, NoneAcceptance> getInitialComponent() {
    return new PowerSetAutomaton<>(ldba.getInitialComponent());
  }

  @Override
  public Map<ValuationSet, Set<T>> getValuationSetJumps(Set<S> state) {
    Map<ValuationSet, Set<T>> jumps = new HashMap<>();
    state.forEach(s -> ldba.getValuationSetJumps(s)
      .forEach((k,v) -> jumps.merge(k, v, Sets::union)));
    return jumps;
  }

  @Override
  public void setVariables(List<String> variables) {
    ldba.setVariables(variables);
  }

  @Override
  public String toString() {
    try {
      return toString(EnumSet.allOf(Option.class));
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }
}