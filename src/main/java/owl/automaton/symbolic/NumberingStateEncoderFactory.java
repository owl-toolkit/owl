/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.automaton.symbolic;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import owl.automaton.Automaton;
import owl.collections.BitSet2;

public final class NumberingStateEncoderFactory implements SymbolicAutomaton.StateEncoderFactory {

  public static final NumberingStateEncoderFactory INSTANCE = new NumberingStateEncoderFactory();

  private NumberingStateEncoderFactory() {}

  @Override
  public <S> SymbolicAutomaton.StateEncoder<S> create(Automaton<? extends S, ?> automaton) {
    Map<S, Integer> numbering = new HashMap<>();

    for (S state : automaton.states()) {
      numbering.put(state, numbering.size());
    }

    if (numbering.size() == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Cannot encode automaton using 32 bits.");
    }

    return new Encoder<>(numbering);
  }

  private static class Encoder<S> implements SymbolicAutomaton.StateEncoder<S> {

    private final int usedBits;
    private final Map<S, Integer> numbering;

    private Encoder(Map<S, Integer> numbering) {
      this.numbering = Map.copyOf(numbering);
      this.usedBits = Integer.SIZE - Integer.numberOfLeadingZeros(this.numbering.size());
    }

    @Override
    public int stateVariables() {
      return usedBits;
    }

    @Override
    public BitSet encode(S s) {
      return BitSet2.fromInt(numbering.get(s));
    }
  }
}
