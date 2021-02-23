/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import owl.bdd.BddSet;
import owl.util.OwlVersion;

public final class HoaWriter {

  private static final Logger LOGGER = Logger.getLogger(HoaWriter.class.getName());

  private HoaWriter() {}

  public static <S> String toString(Automaton<S, ?> automaton) {
    return toString(automaton, EnumSet.of(HoaOption.ANNOTATIONS));
  }

  public static <S> String toString(Automaton<S, ?> automaton, EnumSet<HoaOption> options) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HoaWriter.write(automaton, new HOAConsumerPrint(writer), options);
    return writer.toString(StandardCharsets.UTF_8);
  }

  public static <S> void write(Automaton<S, ?> automaton, HOAConsumer consumer) {
    write(automaton, consumer, EnumSet.noneOf(HoaOption.class));
  }

  public static <S> void write(Automaton<S, ?> automaton, HOAConsumer consumer,
    EnumSet<HoaOption> options) {

    try {
      StateId<S> hoa = new StateId<>();

      consumer.notifyHeaderStart("v1");
      var nameAndVersion = OwlVersion.getNameAndVersion();
      consumer.setTool(nameAndVersion.name(), nameAndVersion.version());

      if (options.contains(HoaOption.ANNOTATIONS)) {
        consumer.setName(automaton.name());
      }

      for (S state : automaton.initialStates()) {
        consumer.addStartStates(List.of(hoa.get(state)));
      }

      OmegaAcceptance acceptance = automaton.acceptance();
      String accName = acceptance.name();

      if (accName != null) {
        consumer.provideAcceptanceName(accName, acceptance.nameExtra());
      }

      consumer.setAcceptanceCondition(acceptance.acceptanceSets(),
        BooleanExpressions.fromPropositionalFormula(acceptance.booleanExpression()));

      // TODO jhoafparser does not adhere to the spec - if we call an automaton without initial
      // states deterministic, the serializer will throw an exception.
      if (!automaton.initialStates().isEmpty()
        && automaton.is(Automaton.Property.DETERMINISTIC)) {

        consumer.addProperties(List.of("deterministic"));
      }

      // TODO: Use Properties.java to derive properties and fix this.
      consumer.addProperties(List.of("trans-acc", "trans-label"));
      consumer.setAPs(automaton.atomicPropositions());
      consumer.notifyBodyStart();

      // Use a work-list algorithm in case source is an on-the-fly generated automaton and
      // to ensure that initial states appear at the top.
      Deque<S> workList = new ArrayDeque<>(automaton.initialStates());
      Set<S> visited = new HashSet<>(workList);

      while (!workList.isEmpty()) {
        S state = workList.remove();
        int stateId = hoa.get(state);

        @Nullable
        String label = options.contains(HoaOption.ANNOTATIONS) ? state.toString() : null;
        consumer.addState(stateId, label, null, null);

        for (Map.Entry<Edge<S>, BddSet> entry : automaton.edgeMap(state).entrySet()) {
          Edge<S> edge = entry.getKey();
          S successor = edge.successor();
          BddSet valuationSet = entry.getValue();

          if (valuationSet.isEmpty()) {
            continue;
          }

          if (visited.add(successor)) {
            workList.add(successor);
          }

          consumer.addEdgeWithLabel(stateId,
            BooleanExpressions.fromPropositionalFormula(valuationSet.toExpression(),
              x -> new BooleanExpression<>(AtomLabel.createAPIndex(x))),
            List.of(hoa.get(edge.successor())),
            Arrays.asList(edge.colours().toArray(Integer[]::new)));
        }

        consumer.notifyEndOfState(stateId);
      }

      consumer.notifyEnd();
    } catch (HOAConsumerException ex) {
      LOGGER.log(Level.SEVERE, "HOAConsumer could not perform API call: ", ex);
    }
  }

  public enum HoaOption {
    /**
     * Print annotations, e.g. state labels, if available
     */
    ANNOTATIONS
  }

  static final class StateId<S> {
    private final Map<S, Integer> stateNumbers = new HashMap<>();

    private int get(S state) {
      return stateNumbers.computeIfAbsent(Objects.requireNonNull(state), k -> stateNumbers.size());
    }
  }
}
