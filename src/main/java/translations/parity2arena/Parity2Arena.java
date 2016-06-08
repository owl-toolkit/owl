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

package translations.parity2arena;


import translations.ldba2parity.ParityAutomaton;

import java.util.BitSet;
import java.util.function.Function;

public class Parity2Arena implements Function<ParityAutomaton, Arena> {

    private final BitSet environmentAlphabet;
    private final Arena.Player firstPlayer;

    public Parity2Arena(BitSet environmentAlphabet) {
        this(environmentAlphabet, Arena.Player.Environment);
    }

    public Parity2Arena(BitSet environmentAlphabet, Arena.Player firstPlayer) {
        this.environmentAlphabet = environmentAlphabet;
        this.firstPlayer = firstPlayer;
    }

    @Override
    public Arena apply(ParityAutomaton automaton) {
        Arena arena = new Arena(automaton, environmentAlphabet, firstPlayer);
        arena.generate();
        return arena;
    }
}
