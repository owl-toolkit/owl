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

package translations.ltl2ldba.ng;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import translations.Optimisation;
import translations.ltl2ldba.AbstractAcceptingComponent;

public class GeneralisedAcceptingComponent extends AbstractAcceptingComponent<GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, RecurringObligations2> {

    GeneralisedAcceptingComponent(Factories factories, EnumSet<Optimisation> optimisations) {
        super(new GeneralisedBuchiAcceptance(1), optimisations, factories);
    }

    @Override
    protected State createState(EquivalenceClass remainder, RecurringObligations2 obligations) {
        EquivalenceClass safety = remainder.andWith(obligations.safety);
        EquivalenceClass liveness[] = new EquivalenceClass[obligations.liveness.length];

        for (int i = 0; i < liveness.length; i++) {
            liveness[i] = factory.getInitial(obligations.liveness[i]);
        }

        // If it is necessary, increase the number of acceptance conditions.
        if (liveness.length > acceptance.getSize()) {
            acceptance = new GeneralisedBuchiAcceptance(liveness.length);
            ACCEPT.set(0, liveness.length);
        }

        return new State(factory.getInitial(safety, liveness), liveness, obligations);
    }

    public final class State extends ImmutableObject implements AutomatonState<State> {

        private final RecurringObligations2 obligations;
        private final EquivalenceClass[] liveness;
        private final EquivalenceClass safety;

        private State(EquivalenceClass safety, EquivalenceClass[] liveness, RecurringObligations2 obligations) {
            this.liveness = liveness;
            this.obligations = obligations;
            this.safety = safety;
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = factory.getSensitiveAlphabet(safety);
                sensitiveAlphabet.or(factory.getSensitiveAlphabet(obligations.safety));

                for (EquivalenceClass clazz : liveness) {
                    sensitiveAlphabet.or(factory.getSensitiveAlphabet(factory.getInitial(clazz)));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Nonnegative
        private int scan(@Nonnegative int i, BitSet valuation) {
            final int livenessLength = obligations.liveness.length;

            while (i < livenessLength) {
                EquivalenceClass successor = factory.getSuccessor(factory.getInitial(obligations.liveness[i]), valuation);

                if (successor.isTrue()) {
                    i++;
                } else {
                    break;
                }
            }

            return i;
        }

        @Nullable
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            EquivalenceClass[] livenessSuccessor = new EquivalenceClass[liveness.length];

            BitSet bs = new BitSet();
            bs.set(liveness.length, acceptance.getSize());

            for (int i = 0; i < liveness.length; i++) {
                livenessSuccessor[i] = factory.getSuccessor(liveness[i], valuation);

                if (livenessSuccessor[i].isTrue()) {
                    bs.set(i);
                    livenessSuccessor[i] = factory.getInitial(obligations.liveness[i]);
                }
            }

            EquivalenceClass safetySuccessor = factory.getSuccessor(safety, valuation, livenessSuccessor)
                    .andWith(factory.getInitial(obligations.safety));

            if (safetySuccessor.isFalse()) {
                return null;
            }

            assert Arrays.stream(livenessSuccessor).noneMatch(EquivalenceClass::isFalse) : "Liveness properties cannot be false.";

            return Edges.create(new State(safetySuccessor, livenessSuccessor, obligations), bs);
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(Arrays.hashCode(liveness), obligations, safety);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return Objects.equals(obligations, that.obligations) && Objects.equals(safety, that.safety) && Arrays.equals(liveness, that.liveness);
        }

        @Override
        public String toString() {
            return "[obligations=" + obligations +
                    (!safety.isTrue() ? ", safety=" + safety : "") +
                    ", liveness=" + Arrays.toString(liveness) +
                    ']';
        }
    }
}