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

package omega_automaton;

import ltl.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

// TODO: migrate to abstract class?
public interface AutomatonState<S> {

    ValuationSetFactory getFactory();

    /**
     * @param valuation
     * @return null is returned if the transition would move to a non-accepting BSCC.
     */
    @Nullable
    S getSuccessor(BitSet valuation);

    @Nonnull
    default Map<S, ValuationSet> getSuccessors() {
        ValuationSetFactory factory = getFactory();
        BitSet sensitiveAlphabet = getSensitiveAlphabet();
        Map<S, ValuationSet> successors = new LinkedHashMap<>();

        for (BitSet valuation : Collections3.powerSet(sensitiveAlphabet)) {
            S successor = getSuccessor(valuation);

            if (successor == null) {
                continue;
            }

            ValuationSet oldVs = successors.get(successor);
            ValuationSet newVs = factory.createValuationSet(valuation, sensitiveAlphabet);

            if (oldVs == null) {
                successors.put(successor, newVs);
            } else {
                oldVs.addAllWith(newVs);
            }
        }

        return successors;
    }

    @Nonnull
    default BitSet getSensitiveAlphabet() {
        BitSet a = new BitSet();
        a.set(0, getFactory().getSize(), true);
        return a;
    }

    @Nullable
    default Map<BitSet,ValuationSet> getAcceptanceIndices() {
        return null;
    }
}
