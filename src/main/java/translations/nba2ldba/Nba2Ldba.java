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

package translations.nba2ldba;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import omega_automaton.StoredBuchiAutomaton;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.output.HOAPrintable;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;

import java.util.EnumSet;
import java.util.function.Function;

public class Nba2Ldba implements Function<StoredBuchiAutomaton, HOAPrintable> {

    private final EnumSet<Optimisation> optimisations;

    public Nba2Ldba(EnumSet<Optimisation> optimisations) {
        this.optimisations = optimisations;
    }

    @Override
    public HOAPrintable apply(StoredBuchiAutomaton nba) {
        if (nba.isDeterministic()) {
            return nba;
        }

        AcceptingComponent acceptingComponent = new AcceptingComponent(nba);
        InitialComponent initialComponent = new InitialComponent(nba, acceptingComponent);
        LimitDeterministicAutomaton<StoredBuchiAutomaton.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent, AcceptingComponent> ldba;
        StoredBuchiAutomaton.State initialState = Iterables.getOnlyElement(nba.getInitialStates());
        ldba = new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, Sets.newHashSet(initialComponent.createState(initialState)), optimisations);
        ldba.generate();
        ldba.setAtomMapping(nba.getAtomMapping());
        return ldba;
    }
}