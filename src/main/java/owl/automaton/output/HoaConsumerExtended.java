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

package owl.automaton.output;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import owl.automaton.LabelledEdge;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaPrintable.Option;
import owl.collections.BitSets;
import owl.collections.ValuationSet;

public class HoaConsumerExtended<S> {
  private static final Logger log = Logger.getLogger(HoaConsumerExtended.class.getName());

  private final HOAConsumer consumer;
  private final EnumSet<HoaPrintable.Option> options;
  private final Map<S, Integer> stateNumbers;
  @Nullable
  private S currentState;

  public HoaConsumerExtended(HOAConsumer consumer, List<String> aliases, OmegaAcceptance acceptance,
    Set<? extends S> initialStates, int size, EnumSet<HoaPrintable.Option> options) {
    checkArgument(initialStates.size() <= size);

    this.consumer = consumer;
    this.options = EnumSet.copyOf(options);
    stateNumbers = new HashMap<>();

    try {
      consumer.notifyHeaderStart("v1");
      consumer.setTool("Owl", "* *"); // Owl in a cave.

      if (options.contains(HoaPrintable.Option.ANNOTATIONS)) {
        consumer.setName("LegacyAutomaton for " + initialStates.toString());
      }

      if (size >= 0) {
        consumer.setNumberOfStates(size);
      }

      if (initialStates.isEmpty()) {
        OmegaAcceptance noneAcceptance = new NoneAcceptance();
        consumer.provideAcceptanceName(noneAcceptance.getName(), noneAcceptance.getNameExtra());
        consumer.setAcceptanceCondition(noneAcceptance.getAcceptanceSets(),
          noneAcceptance.getBooleanExpression());
      } else {
        for (S state : initialStates) {
          consumer.addStartStates(Collections.singletonList(getStateId(state)));
        }

        consumer.provideAcceptanceName(acceptance.getName(), acceptance.getNameExtra());
        consumer.setAcceptanceCondition(acceptance.getAcceptanceSets(),
          acceptance.getBooleanExpression());
      }

      consumer.setAPs(aliases);
      consumer.notifyBodyStart();

      if (initialStates.isEmpty() || size == 0) {
        consumer.notifyEnd();
      }
    } catch (HOAConsumerException ex) {
      log.log(Level.WARNING, "Error during HOA writing", ex);
    }
  }

  public static BooleanExpression<AtomAcceptance> mkFin(int number) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_FIN, number, false));
  }

  public static BooleanExpression<AtomAcceptance> mkInf(int number) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_INF, number, false));
  }

  public void addEdge(ValuationSet label, S end) {
    addEdgeBackend(label, end, null);
  }

  public void addEdge(ValuationSet label, S end, PrimitiveIterator.OfInt accSets) {
    addEdgeBackend(label, end, BitSets.toList(accSets));
  }

  public void addEdge(LabelledEdge<? extends S> labelledEdge) {
    addEdge(labelledEdge.edge, labelledEdge.valuations);
  }

  public void addEdge(Edge<? extends S> edge, ValuationSet label) {
    addEdge(label, edge.getSuccessor(), edge.acceptanceSetIterator());
  }

  private void addEdgeBackend(ValuationSet label, S end, @Nullable IntList accSets) {
    checkState(currentState != null);

    if (label.isEmpty()) {
      return;
    }

    try {
      consumer.addEdgeWithLabel(getStateId(currentState), label.toExpression(),
        Collections.singletonList(getStateId(end)), accSets);
    } catch (HOAConsumerException ex) {
      log.log(Level.WARNING, "Error during HOA writing", ex);
    }
  }

  public void addEpsilonEdge(S successor) {
    checkState(currentState != null);
    log.log(Level.WARNING, "Warning: HOA currently does not support epsilon-transitions. "
      + "({0} -> {1})", new Object[] {currentState, successor});

    try {
      consumer.addEdgeWithLabel(getStateId(currentState), null,
        Collections.singletonList(getStateId(successor)), null);
    } catch (HOAConsumerException ex) {
      log.log(Level.WARNING, "Error during HOA writing", ex);
    }
  }

  public void addState(S state) {
    try {
      currentState = state;
      @Nullable
      String label = options.contains(Option.ANNOTATIONS) ? state.toString() : null;
      consumer.addState(getStateId(state), label, null, null);
    } catch (HOAConsumerException ex) {
      log.log(Level.WARNING, "Error during HOA writing", ex);
    }
  }

  private int getStateId(S state) {
    return stateNumbers.computeIfAbsent(state, k -> stateNumbers.size());
  }

  public void notifyEnd() {
    try {
      if (!stateNumbers.isEmpty()) {
        consumer.notifyEnd();
      }
    } catch (HOAConsumerException ex) {
      log.log(Level.WARNING, "Error during HOA writing", ex);
    }
  }

  public void notifyEndOfState() {
    checkState(currentState != null);
    try {
      consumer.notifyEndOfState(getStateId(currentState));
    } catch (HOAConsumerException ex) {
      log.log(Level.WARNING, "Error during HOA writing", ex);
    }
  }
}
