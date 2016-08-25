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

import omega_automaton.acceptance.ParityAcceptance;
import translations.ltl2ldba.AcceptingComponent;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

class WrappedParityAutomaton extends ParityAutomaton<AcceptingComponent.State> {

    private AcceptingComponent ba;

    WrappedParityAutomaton(AcceptingComponent ba) {
        super(ba, new ParityAcceptance(1));
        this.ba = ba;
    }

    @Nonnull
    @Override
    protected AcceptingComponent.State generateRejectingTrap() {
        return ba.generateRejectingTrap();
    }
}
