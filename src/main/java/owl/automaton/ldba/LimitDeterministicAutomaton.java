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

package owl.automaton.ldba;

import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.Automaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.output.HoaConsumerExtended;
import owl.automaton.output.HoaPrintable;
import owl.collections.ValuationSet;

public interface LimitDeterministicAutomaton<S, T, U extends GeneralizedBuchiAcceptance, V>
  extends HoaPrintable {
  default CutDeterministicAutomaton<S, T, U, V> asCutDeterministicAutomaton() {
    return new CutDeterministicAutomaton<>(this);
  }

  Automaton<T, U> getAcceptingComponent();

  @Nullable
  V getAnnotation(T key);

  Set<V> getComponents();

  Set<T> getEpsilonJumps(S state);

  Automaton<S, NoneAcceptance> getInitialComponent();

  Map<ValuationSet, Set<T>> getValuationSetJumps(S state);

  default boolean isDeterministic() {
    return getInitialComponent().size() == 0
      && getAcceptingComponent().getInitialStates().size() <= 1;
  }

  default int size() {
    return getInitialComponent().size() + getAcceptingComponent().size();
  }

  @Override
  default void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended<Object> consumerExt = new HoaConsumerExtended<>(consumer,
      getAcceptingComponent().getVariables(),
      getAcceptingComponent().getAcceptance(),
      Sets.union(getInitialComponent().getInitialStates(),
        getAcceptingComponent().getInitialStates()),
      options, false, getName());

    getInitialComponent().forEachState(state -> {
      consumerExt.addState(state);
      getInitialComponent().forEachLabelledEdge(state, consumerExt::addEdge);
      getEpsilonJumps(state).forEach(consumerExt::addEpsilonEdge);
      getValuationSetJumps(state).forEach((a, b) -> b.forEach(d -> consumerExt.addEdge(a, d)));
      consumerExt.notifyEndOfState();
    });

    getAcceptingComponent().forEachState(state -> {
      consumerExt.addState(state);
      getAcceptingComponent().forEachLabelledEdge(state, consumerExt::addEdge);
      consumerExt.notifyEndOfState();
    });

    consumerExt.notifyEnd();
  }

  default String toString(EnumSet<HoaOption> options) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      toHoa(new HOAConsumerPrint(stream), options);
      return stream.toString("UTF8");
    }
  }
}
