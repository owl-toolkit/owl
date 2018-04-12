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

import static com.google.common.base.Preconditions.checkState;
import static owl.automaton.output.HoaPrintable.HoaOption.SIMPLE_TRANSITION_LABELS;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import owl.automaton.acceptance.BooleanExpressions;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaPrintable.HoaOption;
import owl.collections.ValuationSet;

public final class HoaConsumerExtended<S> {
  // TODO If --annotations is there, try to print the complex edge expressions in toHOA

  private static final Logger log = Logger.getLogger(HoaConsumerExtended.class.getName());

  private final int alphabetSize;
  private final HOAConsumer consumer;
  private final EnumSet<HoaOption> options;
  private final Object2IntMap<S> stateNumbers;
  @Nullable
  private S currentState = null;

  public HoaConsumerExtended(HOAConsumer consumer, List<String> aliases, OmegaAcceptance acceptance,
    Set<? extends S> initialStates, EnumSet<HoaOption> options, boolean isDeterministic,
    @Nullable String name) {
    this.consumer = consumer;
    this.options = EnumSet.copyOf(options);
    this.stateNumbers = new Object2IntOpenHashMap<>();
    alphabetSize = aliases.size();

    try {
      consumer.notifyHeaderStart("v1");
      consumer.setTool("owl", "* *"); // Owl in a cave.

      if (options.contains(HoaOption.ANNOTATIONS)) {
        consumer
          .setName(Objects.requireNonNullElseGet(name, () -> "Automaton for " + initialStates));
      }

      if (initialStates.isEmpty()) {
        OmegaAcceptance noneAcceptance = NoneAcceptance.INSTANCE;
        consumer.provideAcceptanceName(noneAcceptance.name(), noneAcceptance.nameExtra());
        consumer.setAcceptanceCondition(noneAcceptance.acceptanceSets(),
          noneAcceptance.booleanExpression());
      } else {
        for (S state : initialStates) {
          consumer.addStartStates(List.of(getStateId(state)));
        }

        String accName = acceptance.name();

        if (accName != null) {
          consumer.provideAcceptanceName(accName, acceptance.nameExtra());
        }

        consumer.setAcceptanceCondition(acceptance.acceptanceSets(),
          acceptance.booleanExpression());

        // TODO jhoafparser does not adhere to the spec - if we call an automaton without initial
        // states deterministic, the serializer will throw an exception.
        if (isDeterministic) {
          consumer.addProperties(List.of("deterministic"));
        }
      }

      // TODO: Use Properties.java to derive properties.

      // TODO: fix this.
      consumer.addProperties(List.of("trans-acc", "trans-label"));
      consumer.setAPs(aliases);

      consumer.notifyBodyStart();

      if (initialStates.isEmpty()) {
        consumer.notifyEnd();
      }
    } catch (HOAConsumerException ex) {
      log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  public void addEdge(ValuationSet label, S end) {
    if (label.isEmpty()) {
      return;
    }

    addEdgeBackend(label.toExpression(), end, IntLists.EMPTY_LIST);
  }

  public void addEdge(ValuationSet label, S end, PrimitiveIterator.OfInt accSets) {
    if (label.isEmpty()) {
      return;
    }

    IntArrayList acceptanceSets = new IntArrayList();
    accSets.forEachRemaining((IntConsumer) acceptanceSets::add);
    if (options.contains(SIMPLE_TRANSITION_LABELS)) {
      label.forEach(bitSet -> addEdgeBackend(toLabel(bitSet), end, acceptanceSets));
    } else {
      addEdgeBackend(label.toExpression(), end, acceptanceSets);
    }
  }

  public void addEdge(LabelledEdge<? extends S> labelledEdge) {
    addEdge(labelledEdge.edge, labelledEdge.valuations);
  }

  public void addEdge(Edge<? extends S> edge, BitSet label) {
    IntArrayList accSets = new IntArrayList();
    edge.acceptanceSetIterator().forEachRemaining((IntConsumer) accSets::add);
    addEdgeBackend(toLabel(label), edge.successor(), accSets);
  }

  public void addEdge(Edge<? extends S> edge, ValuationSet label) {
    addEdge(label, edge.successor(), edge.acceptanceSetIterator());
  }

  private void addEdgeBackend(BooleanExpression<AtomLabel> label, S end, IntList accSets) {
    checkState(currentState != null);

    try {
      consumer.addEdgeWithLabel(getStateId(currentState), label,
        List.of(getStateId(end)), accSets.isEmpty() ? null : accSets);
    } catch (HOAConsumerException ex) {
      log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  public void addEpsilonEdge(S successor) {
    checkState(currentState != null);
    log.log(Level.FINER, "HOA currently does not support epsilon-transitions. "
      + "({0} -> {1})", new Object[] {currentState, successor});

    try {
      consumer.addEdgeWithLabel(getStateId(currentState), null,
        List.of(getStateId(successor)), null);
    } catch (HOAConsumerException ex) {
      log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  public void addState(S state) {
    try {
      currentState = state;
      @Nullable
      String label = options.contains(HoaOption.ANNOTATIONS) ? state.toString() : null;
      consumer.addState(getStateId(state), label, null, null);
    } catch (HOAConsumerException ex) {
      log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  private int getStateId(S state) {
    return stateNumbers.computeIntIfAbsent(state, k -> stateNumbers.size());
  }

  public void notifyEnd() {
    try {
      if (!stateNumbers.isEmpty()) {
        consumer.notifyEnd();
      }
    } catch (HOAConsumerException ex) {
      log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  public void notifyEndOfState() {
    checkState(currentState != null);
    try {
      consumer.notifyEndOfState(getStateId(currentState));
    } catch (HOAConsumerException ex) {
      log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  private BooleanExpression<AtomLabel> toLabel(BitSet label) {
    List<BooleanExpression<AtomLabel>> conjuncts = new ArrayList<>(alphabetSize);

    for (int i = 0; i < alphabetSize; i++) {
      BooleanExpression<AtomLabel> atom = new BooleanExpression<>(AtomLabel.createAPIndex(i));

      if (label.get(i)) {
        conjuncts.add(atom);
      } else {
        conjuncts.add(atom.not());
      }
    }

    return BooleanExpressions.createConjunction(conjuncts);
  }
}
