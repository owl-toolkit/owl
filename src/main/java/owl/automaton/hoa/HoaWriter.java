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

package owl.automaton.hoa;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.thirdparty.jhoafparser.ast.AtomLabel;
import owl.thirdparty.jhoafparser.consumer.HOAConsumer;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;
import owl.thirdparty.jhoafparser.owl.extensions.HOAConsumerPrintFixed;
import owl.util.OwlVersion;

public final class HoaWriter {

  private HoaWriter() {}

  public static <S> String toString(Automaton<S, ?> automaton) {
    var buffer = new StringWriter();

    try {
      write(automaton, new HOAConsumerPrintFixed(buffer), true);
    } catch (HOAConsumerException ex) {
      throw new UncheckedHoaConsumerException(ex);
    }

    return buffer.toString();
  }

  public static <S> void write(
    Automaton<S, ?> automaton, HOAConsumer consumer, boolean stateLabels)
    throws HOAConsumerException {

    write(automaton, consumer, stateLabels, null, null, null);
  }

  public static <S> void write(
    Automaton<S, ?> automaton,
    HOAConsumer consumer,
    boolean stateLabels,
    @Nullable String subcommand,
    @Nullable List<String> subcommandArgs,
    @Nullable String automatonName)
    throws HOAConsumerException {

    consumer.notifyHeaderStart("v1");
    var nameAndVersion = OwlVersion.getNameAndVersion();
    consumer.setTool(
      subcommand == null ? nameAndVersion.name() : nameAndVersion.name() + ' ' + subcommand,
      nameAndVersion.version());

    if (automatonName != null) {
      consumer.setName(automatonName.replace('"', '\''));
    }

    if (subcommandArgs != null) {
      List<Object> owlArgsQuoted = Arrays.asList(subcommandArgs.toArray());
      owlArgsQuoted.replaceAll(x -> '"' + x.toString().replace('"', '\'') + '"');
      consumer.addMiscHeader("owlArgs", owlArgsQuoted);
    }

    var numbering = new Numbering<S>();

    for (S state : automaton.initialStates()) {
      consumer.addStartStates(List.of(numbering.get(state)));
    }

    var acceptance = automaton.acceptance();

    if (acceptance.name() != null) {
      consumer.provideAcceptanceName(acceptance.name(), acceptance.nameExtra());
    }

    consumer.setAcceptanceCondition(acceptance.acceptanceSets(), acceptance.booleanExpression().nnf());
    consumer.addProperties(List.of("trans-acc", "no-univ-branch"));

    // jhoafparser does not adhere to the spec. If we call an automaton without initial
    // states deterministic, the serializer will throw an exception.
    if (!automaton.initialStates().isEmpty()
      && automaton.is(Automaton.Property.DETERMINISTIC)) {
      consumer.addProperties(List.of("deterministic", "unambiguous"));
    }

    if (automaton.is(Automaton.Property.COMPLETE)) {
      consumer.addProperties(List.of("complete"));
    }

    consumer.setAPs(automaton.atomicPropositions());
    consumer.notifyBodyStart();

    // Use a work-list algorithm in case source is an on-the-fly generated automaton and
    // to ensure that initial states appear at the top.
    Deque<S> workList = new ArrayDeque<>(automaton.initialStates());
    Set<S> visited = new HashSet<>(workList);

    while (!workList.isEmpty()) {
      S state = workList.remove();
      int stateId = numbering.get(state);

      @Nullable
      String label = stateLabels ? state.toString() : null;
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
          valuationSet.toExpression().map(AtomLabel::createAPIndex),
          List.of(numbering.get(edge.successor())),
          edge.colours());
      }

      consumer.notifyEndOfState(stateId);
    }

    consumer.notifyEnd();
  }

  static final class Numbering<S> {
    private final Map<S, Integer> stateNumbers = new HashMap<>();

    private int get(S state) {
      return stateNumbers.computeIfAbsent(Objects.requireNonNull(state), k -> stateNumbers.size());
    }
  }

  public static class UncheckedHoaConsumerException extends RuntimeException {
    public UncheckedHoaConsumerException(HOAConsumerException cause) {
      super(cause);
    }
  }
}
