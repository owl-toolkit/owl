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

import com.google.common.collect.BiMap;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Collections3;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import translations.Optimisation;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.algorithms.SCCAnalyser;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.output.HOAConsumerExtended;
import omega_automaton.output.RemoveComments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class LimitDeterministicAutomaton<S_I extends AutomatonState<S_I>, S_A extends AutomatonState<S_A>, Acc extends OmegaAcceptance, I extends AbstractInitialComponent<S_I, S_A>, A extends Automaton<S_A, Acc>> {

    @Nullable
    private final I initialComponent;
    private final A acceptingComponent;
    private final EnumSet<Optimisation> optimisations;

    public LimitDeterministicAutomaton(@Nullable I initialComponent, A acceptingComponent, EnumSet<Optimisation> optimisations) {
        this.initialComponent = initialComponent;
        this.acceptingComponent = acceptingComponent;
        this.optimisations = optimisations;
    }

    public AutomatonState<?> getInitialState() {
        if (initialComponent != null) {
            return initialComponent.getInitialState();
        }

        return acceptingComponent.getInitialState();
    }

    public int size() {
        if (initialComponent == null) {
            return acceptingComponent.size();
        }

        return acceptingComponent.size() + initialComponent.size();
    }

    public void generate() {
        if (initialComponent != null) {
            initialComponent.generate();

            // Generate Jump Table
            List<Set<S_I>> sccs = optimisations.contains(Optimisation.SCC_ANALYSIS) ? SCCAnalyser.SCCsStates(initialComponent) : Collections.singletonList(initialComponent.getStates());

            for (Set<S_I> scc : sccs) {
                // Skip non-looping states with successors of a singleton SCC.
                if (scc.size() == 1) {
                    S_I state = Collections3.getElement(scc);

                    if (initialComponent.isTransient(state) && initialComponent.hasSuccessors(state)) {
                        continue;
                    }
                }

                for (S_I state : scc) {
                    initialComponent.generateJumps(state);
                }
            }

            if (optimisations.contains(Optimisation.REMOVE_EPSILON_TRANSITIONS)) {
                Set<S_A> accReach = new HashSet<>();

                for (S_I state : initialComponent.getStates()) {
                    Map<S_I, ValuationSet> successors = initialComponent.getSuccessors(state);
                    Map<ValuationSet, List<S_A>> successorJumps = initialComponent.valuationSetJumps.row(state);

                    successors.forEach((successor, vs) -> {
                        // Copy successors to a new collection, since clear() will also empty these collections.
                        List<S_A> targets = new ArrayList<>(initialComponent.epsilonJumps.get(successor));
                        accReach.addAll(targets);
                        successorJumps.put(vs, targets);
                    });
                }

                initialComponent.epsilonJumps.clear();
                initialComponent.removeUnreachableStates();
                acceptingComponent.removeUnreachableStates(accReach);
                initialComponent.removeDeadEnds(initialComponent.valuationSetJumps.rowKeySet());
            }
        } else {
            acceptingComponent.generate();
        }
    }

    public void toHOA(HOAConsumer c, @Nonnull BiMap<String, Integer> aliases) throws HOAConsumerException {
        HOAConsumerExtended consumer = new HOAConsumerExtended(c, acceptingComponent.getFactory(), aliases, acceptingComponent.acceptance, getInitialState(), size());

        if (initialComponent != null) {
            initialComponent.toHOABody(consumer);
        }

        acceptingComponent.toHOABody(consumer);
        consumer.done();
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean removeComments) {
        return toString(removeComments, null);
    }

    public String toString(boolean removeComments, BiMap<String, Integer> aliases) {
        try (OutputStream stream = new ByteArrayOutputStream()) {
            HOAConsumer consumer = removeComments ? new RemoveComments(new HOAConsumerPrint(stream)) : new HOAConsumerPrint(stream);
            toHOA(consumer, aliases);
            return stream.toString();
        } catch (IOException | HOAConsumerException ex) {
            throw new IllegalStateException(ex.toString());
        }
    }

    public Set<S_A> getEpsilonJumps(S_I s) {
        return initialComponent.epsilonJumps.get(s);
    }

    public Map<ValuationSet, List<S_A>> getValuationSetJumps(S_I s) {
        return initialComponent.valuationSetJumps.row(s);
    }

    @Nullable
    public I getInitialComponent() {
        return initialComponent;
    }

    public A getAcceptingComponent() {
        return acceptingComponent;
    }
}
