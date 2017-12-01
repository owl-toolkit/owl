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

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
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
    Set<T> set = new HashSet<>();
    state.forEach(s -> set.addAll(ldba.getEpsilonJumps(s)));
    return set;
  }

  @Override
  public Automaton<Set<S>, NoneAcceptance> getInitialComponent() {
    return MutableAutomatonFactory.createMutableAutomaton(Views.createPowerSetAutomaton(
      ldba.getInitialComponent()));
  }

  @Override
  public Map<ValuationSet, Set<T>> getValuationSetJumps(Set<S> state) {
    Map<ValuationSet, Set<T>> jumps = new HashMap<>();
    state.forEach(s -> ldba.getValuationSetJumps(s)
      .forEach((k, v) -> jumps.merge(k, v, Sets::union)));
    return jumps;
  }

  @Override
  public List<String> getVariables() {
    return ldba.getVariables();
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
