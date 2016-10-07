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

package omega_automaton.output;

import com.google.common.collect.BiMap;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.AllAcceptance;
import omega_automaton.acceptance.GeneralisedRabinAcceptance;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.Map.Entry;

public class HOAConsumerGeneralisedRabin<S extends AutomatonState<?>> extends HOAConsumerExtended {

    private GeneralisedRabinAcceptance<S> acceptance;

    public HOAConsumerGeneralisedRabin(@Nonnull HOAConsumer hoa, ValuationSetFactory valuationSetFactory, BiMap<String, Integer> aliases, S initialState,
                                       @Nonnull GeneralisedRabinAcceptance<S> accCond, int size) {
        super(hoa, valuationSetFactory, aliases, accCond, initialState, size, EnumSet.allOf(HOAPrintable.Option.class));
        this.acceptance = accCond;

        Map<String, List<Object>> map = acceptance.miscellaneousAnnotations();

        try {
            for (Entry<String, List<Object>> entry : map.entrySet()) {
                hoa.addMiscHeader(entry.getKey(), entry.getValue());
            }
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    @Override
    public void addEdge(ValuationSet key, AutomatonState<?> end) {
        Set<ValuationSet> realEdges = acceptance.getMaximallyMergedEdgesOfEdge(currentState, key);

        for (ValuationSet edgeKey : realEdges) {
            addEdgeBackend(edgeKey, end, acceptance.getInvolvedAcceptanceNumbers(currentState, edgeKey));
        }
    }
}

