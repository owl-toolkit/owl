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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.AutomatonState;
import owl.automaton.acceptance.NoneAcceptance;
import owl.collections.ValuationSet;
import owl.automaton.output.HOAConsumerExtended;
import owl.factories.Factories;

public abstract class AbstractInitialComponent<S extends AutomatonState<S>, T extends AutomatonState<T>>
  extends Automaton<S, NoneAcceptance> {

  protected final SetMultimap<S, T> epsilonJumps;
  final Table<S, ValuationSet, Set<T>> valuationSetJumps;

  protected AbstractInitialComponent(Factories factories) {
    super(new NoneAcceptance(), factories);
    epsilonJumps = LinkedHashMultimap.create();
    valuationSetJumps = HashBasedTable.create();
  }

  public Set<T> getEpsilonJumps(S state) {
    return Collections.unmodifiableSet(epsilonJumps.get(state));
  }

  public Map<ValuationSet, Set<T>> getValuationSetJumps(S state) {
    return Collections.unmodifiableMap(valuationSetJumps.row(state));
  }

  public abstract void generateJumps(S state);

  void removeDeadEnds(Set<S> s_is) {
    boolean stop = false;

    while (!stop) {
      Collection<S> scan = getStates().stream().filter(s -> !hasSuccessors(s) && !s_is.contains(s))
        .collect(Collectors.toSet());

      if (scan.isEmpty()) {
        stop = true;
      } else {
        removeStatesIf(scan::contains);
      }
    }
  }

  @Override
  protected void toHOABodyEdge(S state, HOAConsumerExtended hoa) {
    super.toHOABodyEdge(state, hoa);

    epsilonJumps.get(state).forEach(hoa::addEpsilonEdge);
    valuationSetJumps.row(state).forEach((vs, targets) -> {
      for (T accState : targets) {
        hoa.addEdge(vs, accState);
      }
    });
  }
}
