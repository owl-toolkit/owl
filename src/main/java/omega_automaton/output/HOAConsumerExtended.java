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

import java.util.logging.Logger;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.NoneAcceptance;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class HOAConsumerExtended {

    static final Logger LOGGER = Logger.getLogger(HOAConsumerExtended.class.getName());

    private final HOAConsumer hoa;
    private final Map<AutomatonState<?>, Integer> stateNumbers;
    private final EnumSet<HOAPrintable.Option> options;
    AutomatonState<?> currentState;

    public HOAConsumerExtended(@Nonnull HOAConsumer hoa, int alphabetSize, @Nonnull Map<Integer, String> aliases, @Nonnull OmegaAcceptance acceptance, AutomatonState<?> initialState,
                               int size, @Nonnull EnumSet<HOAPrintable.Option> options) {
        this.hoa = hoa;
        this.options = options;

        stateNumbers = new HashMap<>(size);

        try {
            hoa.notifyHeaderStart("v1");
            hoa.setTool("Owl", "* *"); // Owl in a cave.

            if (options.contains(HOAPrintable.Option.ANNOTATIONS)) {
                hoa.setName("Automaton for " + ((initialState != null) ? initialState.toString() : "false"));
            }

            if (size >= 0) {
                hoa.setNumberOfStates(size);
            }

            if (initialState != null && size > 0) {
                hoa.addStartStates(Collections.singletonList(getStateId(initialState)));

                if (acceptance.getName() != null) {
                    hoa.provideAcceptanceName(acceptance.getName(), acceptance.getNameExtra());
                }

                hoa.setAcceptanceCondition(acceptance.getAcceptanceSets(), acceptance.getBooleanExpression());
            } else {
                OmegaAcceptance acceptance1 = new NoneAcceptance();
                hoa.provideAcceptanceName(acceptance1.getName(), acceptance1.getNameExtra());
                hoa.setAcceptanceCondition(acceptance1.getAcceptanceSets(), acceptance1.getBooleanExpression());
            }

            hoa.setAPs(IntStream.range(0, alphabetSize).mapToObj(aliases::get).collect(Collectors.toList()));
            hoa.notifyBodyStart();

            if (initialState == null || size == 0) {
                hoa.notifyEnd();
            }
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    public static BooleanExpression<AtomAcceptance> mkInf(int number) {
        return new BooleanExpression<>(new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_INF, number, false));
    }

    public static BooleanExpression<AtomAcceptance> mkFin(int number) {
        return new BooleanExpression<>(new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_FIN, number, false));
    }

    public void addState(AutomatonState<?> state) {
        try {
            currentState = state;
            hoa.addState(getStateId(state), options.contains(HOAPrintable.Option.ANNOTATIONS) ? state.toString() : null, null, null);
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    public void stateDone() {
        try {
            hoa.notifyEndOfState(getStateId(currentState));
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    public void done() {
        try {
            if (!stateNumbers.isEmpty()) {
                hoa.notifyEnd();
            }
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    public void addEdge(ValuationSet key, AutomatonState<?> end) {
        addEdgeBackend(key, end, null);
    }

    public void addEdge(ValuationSet label, AutomatonState<?> end, BitSet accSets) {
        addEdgeBackend(label, end, Collections3.toList(accSets));
    }

    public void addEpsilonEdge(AutomatonState<?> successor) {
        try {
            LOGGER.warning("Warning: HOA currently does not support epsilon-transitions. (" + currentState + " -> " + successor + ')');
            hoa.addEdgeWithLabel(getStateId(currentState), null, Collections.singletonList(getStateId(successor)), null);
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    void addEdgeBackend(ValuationSet label, AutomatonState<?> end, List<Integer> accSets) {
        if (label.isEmpty()) {
            return;
        }

        try {
            hoa.addEdgeWithLabel(getStateId(currentState), label.toExpression(), Collections.singletonList(getStateId(end)), accSets);
        } catch (HOAConsumerException ex) {
            LOGGER.warning(ex.toString());
        }
    }

    private int getStateId(AutomatonState<?> state) {
        return stateNumbers.computeIfAbsent(state, k -> stateNumbers.size());
    }
}
