/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.automaton.hoa;

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.extensions.BooleanExpressions;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

public final class HoaWriter {

  private HoaWriter() {
  }

  public static <S> String toString(Automaton<S, ?> automaton) {
    return toString(automaton, EnumSet.of(HoaOption.ANNOTATIONS));
  }

  public static <S> String toString(Automaton<S, ?> automaton, EnumSet<HoaOption> options) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HoaWriter.write(automaton, new HOAConsumerPrint(writer), options);
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
  }

  public static <S> void write(Automaton<S, ?> automaton, HOAConsumer consumer) {
    write(automaton, consumer, EnumSet.noneOf(HoaOption.class));
  }

  public static <S> void write(Automaton<S, ?> automaton, HOAConsumer consumer,
    EnumSet<HoaOption> options) {
    Wrapper<S> hoa = new Wrapper<>(consumer, automaton.factory().alphabet(),
      automaton.acceptance(), automaton.initialStates(), options,
      automaton.is(Automaton.Property.DETERMINISTIC), automaton.name());
    automaton.accept((Automaton.Visitor<S>) hoa.visitor);
    hoa.done();
  }

  public enum HoaOption {
    /**
     * Print annotations, e.g. state labels, if available
     */
    ANNOTATIONS
  }

  static final class Wrapper<S> {
    private static final Logger log = Logger.getLogger(Wrapper.class.getName());

    private final int alphabetSize;
    private final HOAConsumer consumer;
    private final EnumSet<HoaOption> options;
    private final Object2IntMap<S> stateNumbers;
    @Nullable
    private S currentState = null;
    final Visitor visitor = new Visitor();

    Wrapper(HOAConsumer consumer, List<String> aliases, OmegaAcceptance acceptance,
      Set<S> initialStates, EnumSet<HoaOption> options, boolean isDeterministic, String name) {
      this.consumer = consumer;
      this.options = EnumSet.copyOf(options);
      this.stateNumbers = new Object2IntOpenHashMap<>();
      alphabetSize = aliases.size();

      try {
        consumer.notifyHeaderStart("v1");
        consumer.setTool(tool(), version());

        if (options.contains(HoaOption.ANNOTATIONS)) {
          consumer.setName(name);
        }

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
        if (!initialStates.isEmpty() && isDeterministic) {
          consumer.addProperties(List.of("deterministic"));
        }

        // TODO: Use Properties.java to derive properties and fix this.
        consumer.addProperties(List.of("trans-acc", "trans-label"));
        consumer.setAPs(aliases);
        consumer.notifyBodyStart();
      } catch (HOAConsumerException ex) {
        log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
      }
    }

    // TODO: global scope?
    private static String tool() {
      String title = Wrapper.class.getPackage().getImplementationTitle();
      return title == null ? "owl" : title;
    }

    // TODO: global scope?
    private static String version() {
      String version = Wrapper.class.getPackage().getImplementationVersion();
      return version == null ? "development" : version;
    }

    private void addEdgeBackend(BooleanExpression<AtomLabel> label, S end, IntList accSets) {
      try {
        consumer.addEdgeWithLabel(getStateId(currentState), label, List.of(getStateId(end)),
          accSets.isEmpty() ? null : accSets);
      } catch (HOAConsumerException ex) {
        log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
      }
    }

    private int getStateId(@Nullable S state) {
      checkState(state != null);
      return stateNumbers.computeIntIfAbsent(state, k -> stateNumbers.size());
    }

    void done() {
      try {
        consumer.notifyEnd();
      } catch (HOAConsumerException ex) {
        log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
      }
    }

    class Visitor implements Automaton.EdgeVisitor<S>, Automaton.EdgeMapVisitor<S> {
      @Override
      public void enter(S state) {
        currentState = state;
        @Nullable
        String label = options.contains(HoaOption.ANNOTATIONS) ? state.toString() : null;

        try {
          consumer.addState(getStateId(state), label, null, null);
        } catch (HOAConsumerException ex) {
          log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
        }
      }

      @Override
      public void exit(S state) {
        checkState(state.equals(currentState));

        try {
          consumer.notifyEndOfState(getStateId(currentState));
        } catch (HOAConsumerException ex) {
          log.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
        }
      }

      @Override
      public void visit(S state, BitSet valuation, Edge<S> edge) {
        IntArrayList accSets = new IntArrayList();
        edge.acceptanceSetIterator().forEachRemaining((IntConsumer) accSets::add);
        List<BooleanExpression<AtomLabel>> conjuncts = new ArrayList<>(alphabetSize);

        for (int i = 0; i < alphabetSize; i++) {
          BooleanExpression<AtomLabel> atom = new BooleanExpression<>(AtomLabel.createAPIndex(i));

          if (valuation.get(i)) {
            conjuncts.add(atom);
          } else {
            conjuncts.add(atom.not());
          }
        }

        addEdgeBackend(BooleanExpressions.createConjunction(conjuncts), edge.successor(), accSets);
      }

      @Override
      public void visit(S state, Map<Edge<S>, ValuationSet> edgeMap) {
        edgeMap.forEach((edge, valuationSet) -> {
          S end = edge.successor();

          if (valuationSet.isEmpty()) {
            return;
          }

          IntArrayList acceptanceSets = new IntArrayList();
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) acceptanceSets::add);
          addEdgeBackend(valuationSet.toExpression(), end, acceptanceSets);
        });
      }
    }
  }
}
