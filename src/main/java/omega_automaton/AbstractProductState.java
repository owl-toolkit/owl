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

import com.google.common.collect.ImmutableMap;
import omega_automaton.collections.Tuple;
import omega_automaton.collections.valuationset.ValuationSet;
import ltl.ImmutableObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public abstract class AbstractProductState<P extends AutomatonState<P>, K, S extends AutomatonState<S>, T> extends ImmutableObject {

    public final P primaryState;
    public final ImmutableMap<K, S> secondaryStates;

    protected AbstractProductState(P primaryState, ImmutableMap<K, S> secondaryStates) {
        this.primaryState = primaryState;
        this.secondaryStates = secondaryStates;
    }

    protected AbstractProductState(P primaryState, Iterable<K> keys, Function<K, S> constructor) {
        this.primaryState = primaryState;

        ImmutableMap.Builder<K, S> builder = ImmutableMap.builder();
        keys.forEach(k -> builder.put(k, constructor.apply(k)));
        this.secondaryStates = builder.build();
    }

    @Override
    public String toString() {
        return "(" + primaryState + "::" + secondaryStates + ')';
    }

    @Nullable
    public T getSuccessor(BitSet valuation) {
        P primarySuccessor = primaryState.getSuccessor(valuation);

        if (primarySuccessor == null) {
            return null;
        }

        ImmutableMap.Builder<K, S> builder = ImmutableMap.builder();

        Set<K> keys = relevantSecondary(primarySuccessor);

        if (keys == null) {
            keys = secondaryStates.keySet();
        }

        for (K key : keys) {
            S secondary = secondaryStates.get(key);

            if (secondary != null) {
                S secondarySuccessor = secondary.getSuccessor(valuation);

                if (secondarySuccessor != null) {
                    builder.put(key, secondarySuccessor);
                } else {
                    return null;
                }
            }
        }

        return constructState(primarySuccessor, builder.build());
    }

    @Nonnull
    public Map<T, ValuationSet> getSuccessors() {
        Map<T, ValuationSet> successors = new LinkedHashMap<>();
        Map<P, ValuationSet> primarySuccessors = getPrimaryAutomaton().getSuccessors(primaryState);

        for (Map.Entry<P, ValuationSet> entry1 : primarySuccessors.entrySet()) {
            Set<K> keys = relevantSecondary(entry1.getKey());

            if (keys == null) {
                keys = secondaryStates.keySet();
            }

            for (Tuple<Map<K, S>, ValuationSet> entry2 : secondaryJointMove(keys, entry1.getValue())) {
                successors.put(constructState(entry1.getKey(), ImmutableMap.copyOf(entry2.left)), entry2.right);
            }
        }

        return successors;
    }

    @Nonnull
    public BitSet getSensitiveAlphabet() {
        BitSet sensitiveLetters = primaryState.getSensitiveAlphabet();

        for (S secondaryState : secondaryStates.values()) {
            sensitiveLetters.or(secondaryState.getSensitiveAlphabet());
        }

        return sensitiveLetters;
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(primaryState, secondaryStates);
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
        AbstractProductState<?, ?, ?, ?> that = (AbstractProductState<?, ?, ?, ?>) o;
        return Objects.equals(primaryState, that.primaryState) &&
                Objects.equals(secondaryStates, that.secondaryStates);
    }

    protected abstract Automaton<P, ?> getPrimaryAutomaton();

    protected abstract Map<K, ? extends Automaton<S, ?>> getSecondaryAutomata();

    @Nullable
    protected Set<K> relevantSecondary(P primaryState) {
        return null;
    }

    protected abstract T constructState(P primaryState, ImmutableMap<K, S> secondaryStates);

    protected Iterable<Tuple<Map<K, S>, ValuationSet>> secondaryJointMove(Set<K> keys, ValuationSet maxVs) {
        Map<K, ? extends Automaton<S, ?>> secondary = getSecondaryAutomata();

        Deque<Tuple<Map<K, S>, ValuationSet>> current = new ArrayDeque<>();
        Deque<Tuple<Map<K, S>, ValuationSet>> next = new ArrayDeque<>();

        current.add(new Tuple<>(Collections.emptyMap(), maxVs));

        for (K key : keys) {
            S state = secondaryStates.get(key);

            if (state == null) {
                state = getSecondaryAutomata().get(key).getInitialState();
            }

            Map<S, ValuationSet> successors = secondary.get(key).getSuccessors(state);

            while (!current.isEmpty()) {
                Tuple<Map<K, S>, ValuationSet> entry1 = current.remove();

                for (Map.Entry<S, ValuationSet> entry2 : successors.entrySet()) {
                    ValuationSet set = entry1.right.intersect(entry2.getValue());

                    if (!set.isEmpty()) {
                        Map<K, S> states = new HashMap<>(entry1.left);
                        states.put(key, entry2.getKey());
                        next.add(new Tuple<>(states, set));
                    }
                }
            }

            Deque<Tuple<Map<K, S>, ValuationSet>> swap = current;
            current = next;
            next = swap;
        }

        return current;
    }
}
