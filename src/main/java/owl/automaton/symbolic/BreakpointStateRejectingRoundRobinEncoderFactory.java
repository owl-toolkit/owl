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

import static owl.translations.canonical.DeterministicConstructions.BreakpointStateRejectingRoundRobin;

import java.util.BitSet;
import owl.automaton.Automaton;
import owl.cinterface.EquivalenceClassEncoder;
import owl.collections.BitSet2;

public final class BreakpointStateRejectingRoundRobinEncoderFactory
  implements SymbolicAutomaton.StateEncoderFactory<BreakpointStateRejectingRoundRobin> {

  public BreakpointStateRejectingRoundRobinEncoderFactory() {}

  @Override
  public SymbolicAutomaton.StateEncoder<BreakpointStateRejectingRoundRobin> create(
    Automaton<? extends BreakpointStateRejectingRoundRobin, ?> automaton) {

    EquivalenceClassEncoder encoder = new EquivalenceClassEncoder();
    encoder.putAll(automaton.states());

    int allBits = automaton.states().stream()
      .mapToInt(s -> encoder.getAllProfile(s).last().orElse(-1))
      .max()
      .orElse(-1) + 1;

    int rejectingBits = automaton.states().stream()
      .mapToInt(s -> encoder.getRejectingProfile(s).last().orElse(-1))
      .max()
      .orElse(-1) + 1;

    int largestDisambiguation
      = automaton.states().stream().mapToInt(encoder::disambiguation).max().orElse(0) + 1;
    int disambiguationBits = Integer.SIZE - Integer.numberOfLeadingZeros(largestDisambiguation);

    return new BreakpointStateRejectingRoundRobinStateEncoder(allBits, rejectingBits,
      disambiguationBits, encoder);
  }

  private static class BreakpointStateRejectingRoundRobinStateEncoder
    implements SymbolicAutomaton.StateEncoder<BreakpointStateRejectingRoundRobin> {
    private final int allBits;
    private final int rejectingBits;
    private final int disambiguationBits;
    private final EquivalenceClassEncoder encoder;

    public BreakpointStateRejectingRoundRobinStateEncoder(int allBits, int rejectingBits,
      int disambiguationBits,
      EquivalenceClassEncoder encoder) {
      this.allBits = allBits;
      this.rejectingBits = rejectingBits;
      this.disambiguationBits = disambiguationBits;
      this.encoder = encoder;
    }

    @Override
    public int stateVariables() {
      return allBits + rejectingBits + disambiguationBits;
    }

    @Override
    public BitSet encode(BreakpointStateRejectingRoundRobin state) {
      BitSet encoding = encoder.getAllProfile(state).copyInto(new BitSet());
      encoder.getRejectingProfile(state).forEach((int i) -> {
        assert !encoding.get(allBits + i);
        encoding.set(allBits + i);
      });
      BitSet2.fromInt(encoder.disambiguation(state)).stream().forEach((int i) -> {
        assert !encoding.get(allBits + rejectingBits + i);
        encoding.set(allBits + rejectingBits + i);
      });
      return encoding;
    }
  }
}
