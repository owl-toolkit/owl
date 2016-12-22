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

package translations.ldba;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.NoneAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import omega_automaton.output.HOAConsumerExtended;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractInitialComponent<S extends AutomatonState<S>, T extends AutomatonState<T>>  extends Automaton<S, NoneAcceptance> {

    public final SetMultimap<S, T> epsilonJumps;
    final Table<S, ValuationSet, List<T>> valuationSetJumps;

    protected AbstractInitialComponent(ValuationSetFactory factory) {
        super(new NoneAcceptance(), factory);

        epsilonJumps = HashMultimap.create();
        valuationSetJumps = HashBasedTable.create();
    }

    public abstract void generateJumps(S state);

    void removeDeadEnds(Set<S> s_is) {
        boolean stop = false;

        while (!stop) {
            Collection<S> scan = getStates().stream().filter(s -> !hasSuccessors(s) && !s_is.contains(s)).collect(Collectors.toSet());

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

        for (T accState : epsilonJumps.get(state)) {
            hoa.addEpsilonEdge(accState);
        }

        for (Map.Entry<ValuationSet, List<T>> entry : valuationSetJumps.row(state).entrySet()) {
            for (T accState : entry.getValue()) {
                hoa.addEdge(entry.getKey(), accState);
            }
        }
    }
}
