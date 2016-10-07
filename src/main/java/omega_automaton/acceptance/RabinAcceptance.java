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

package omega_automaton.acceptance;

import omega_automaton.AutomatonState;
import omega_automaton.collections.TranSet;
import omega_automaton.collections.Tuple;

import java.util.Collections;
import java.util.List;

public class RabinAcceptance<S extends AutomatonState<?>> extends GeneralisedRabinAcceptance<S> {

    public RabinAcceptance(List<Tuple<TranSet<S>, List<TranSet<S>>>> acceptanceCondition) {
        super(acceptanceCondition);
        checkIfValidArgument(acceptanceCondition);
    }

    private void checkIfValidArgument(List<Tuple<TranSet<S>, List<TranSet<S>>>> acceptanceCondition) {
        for (Tuple<TranSet<S>, List<TranSet<S>>> pair : acceptanceCondition) {
            if (pair.right.size() != 1) {
                throw new IllegalArgumentException("Acceptance condition is not a Rabin condition.");
            }
        }

    }

    @Override
    public String getName() {
        return "Rabin";
    }

    @Override
    public List<Object> getNameExtra() {
        return Collections.singletonList(this.acceptanceCondition.size());
    }

    public void addRabinPair(TranSet<S> fin, TranSet<S> inf) {
        this.acceptanceCondition.add(new Tuple<>(fin.copy(), Collections.singletonList(inf.copy())));
    }
}
