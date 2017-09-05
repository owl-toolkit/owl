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

package owl.translations.frequency;

import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.translations.frequency.FrequencyMojmirSlaveAutomaton.MojmirState;

public final class FrequencySelfProductSlave extends
  Automaton<FrequencySelfProductSlave.State, AllAcceptance> {

  final FrequencyMojmirSlaveAutomaton mojmir;
  final BitSet sensitiveAlphabet;

  FrequencySelfProductSlave(FrequencyMojmirSlaveAutomaton mojmir, Factories factories) {
    super(AllAcceptance.INSTANCE, factories);
    this.mojmir = mojmir;
    sensitiveAlphabet = new BitSet();
    sensitiveAlphabet.set(0, factories.vsFactory.alphabetSize());
    State s = new State();
    s.put(mojmir.getInitialState(), 1);
    setInitialState(s);
  }

  @Override
  protected void toHoaBodyEdge(State state, HoaConsumerExtended hoa) {
    // empty
  }

  public class State extends HashMap<FrequencyMojmirSlaveAutomaton.MojmirState, Integer>
    implements AutomatonState<State> {
    private static final long serialVersionUID = 1L;

    FrequencySelfProductSlave getOuter() {
      return FrequencySelfProductSlave.this;
    }

    @Nonnull
    @Override
    public BitSet getSensitiveAlphabet() {
      return sensitiveAlphabet;
    }

    @Override
    public Edge<State> getSuccessor(BitSet valuation) {
      // Move tokens, make use of acyclicity:
      List<Set<FrequencyMojmirSlaveAutomaton.MojmirState>> sccStates =
        SccAnalyser.computeSccs(mojmir.getStates(), state ->
          mojmir.getSuccessors(state).keySet().stream()
            .map(Edge::successor).collect(Collectors.toSet()));

      sccStates.removeIf(set -> set.size() == 1 && mojmir.isSink(Iterables.getOnlyElement(set)));

      State successor = new State();
      for (Set<FrequencyMojmirSlaveAutomaton.MojmirState> stateSet : sccStates) {
        assert stateSet.size() == 1;
        MojmirState s = Iterables.getOnlyElement(stateSet);
        FrequencyMojmirSlaveAutomaton.MojmirState succMojmir =
          s.getSuccessor(valuation).successor();
        if (!mojmir.isSink(succMojmir)) {
          int successorValue = successor.getOrDefault(succMojmir, 0);
          successor.put(succMojmir, (this.get(s) == null ? 0 : this.get(s)) + successorValue);
        }
        successor.put(s, 0);
      }

      // add initial token
      successor.put(mojmir.getInitialState(), 1 + (successor.get(mojmir.getInitialState()) == null
        ? 0 : successor.get(mojmir.getInitialState())));

      // remove unneccessary tokens
      successor.values().removeIf(stateIntegerEntry -> stateIntegerEntry < 1);

      return Edge.of(successor);
    }
  }
}
