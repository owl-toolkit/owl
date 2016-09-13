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

package translations.ltl2parity;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.Edge;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ParityAutomaton<S extends AutomatonState<S>> extends Automaton<S, ParityAcceptance> {

    // fb-contrib does not honour the restrictions made by the type parameter.
    @SuppressFBWarnings("ocp")
    ParityAutomaton(ParityAcceptance acceptance, ValuationSetFactory factory, AtomicInteger integer) {
        super(acceptance, factory, integer);
    }

    @SuppressFBWarnings("ocp")
    ParityAutomaton(Automaton<S, ?> ba, ParityAcceptance acceptance) {
        super(ba, acceptance);
    }

    @Nonnull
    @Override
    protected Edge<S> generateRejectingEdge(S successor) {
        BitSet bs = new BitSet();
        bs.set(acceptance.getPriority() == ParityAcceptance.Priority.ODD ? 0 : 1);
        return new Edge<>(successor, bs);
    }

    void complement() {
        complete();
        acceptance = acceptance.complement();
    }
}
