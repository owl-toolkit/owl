/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.automaton.output;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.collections.ValuationSet;
import owl.util.TypeUtil;

public final class HoaPrinter {

  private HoaPrinter() {
  }

  public static <S> String toString(Automaton<S, ?> automaton) {
    return toString(automaton, EnumSet.of(HoaOption.ANNOTATIONS));
  }

  public static <S> String toString(Automaton<S, ?> automaton, EnumSet<HoaOption> options) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HoaPrinter.feedTo(automaton, new HOAConsumerPrint(writer), options);
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
  }

  public static <S> String toString(LimitDeterministicAutomaton<S, ?, ?, ?> automaton) {
    return toString(automaton, EnumSet.of(HoaOption.ANNOTATIONS));
  }

  public static <S> String toString(LimitDeterministicAutomaton<S, ?, ?, ?> automaton,
    EnumSet<HoaOption> options) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HoaPrinter.feedTo(automaton, new HOAConsumerPrint(writer), options);
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
  }

  public static void feedTo(Object automaton, HOAConsumer consumer) {
    feedTo(automaton, consumer, EnumSet.noneOf(HoaOption.class));
  }

  public static void feedTo(Object automaton, HOAConsumer consumer, EnumSet<HoaOption> options) {
    checkArgument(automaton instanceof Automaton
      || automaton instanceof LimitDeterministicAutomaton);

    if (automaton instanceof Automaton) {
      HoaPrinter.feedTo((Automaton) automaton, consumer, options);
    } else {
      HoaPrinter.feedTo((LimitDeterministicAutomaton) automaton, consumer, options);
    }
  }

  public static <S> void feedTo(Automaton<S, ?> automaton, HOAConsumer consumer) {
    feedTo(automaton, consumer, EnumSet.noneOf(HoaOption.class));
  }

  public static <S> void feedTo(Automaton<S, ?> automaton, HOAConsumer consumer,
    EnumSet<HoaOption> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, automaton.factory().alphabet(),
      automaton.acceptance(), automaton.initialStates(), options,
      automaton.is(Automaton.Property.DETERMINISTIC), automaton.name());
    automaton.accept(hoa.visitor);
    hoa.done();
  }

  public static <S> void feedTo(LimitDeterministicAutomaton<S, ?, ?, ?> ldba,
    HOAConsumer consumer) {
    feedTo(ldba, consumer, EnumSet.noneOf(HoaOption.class));
  }

  public static <S> void feedTo(LimitDeterministicAutomaton<S, ?, ?, ?> ldba, HOAConsumer consumer,
    EnumSet<HoaOption> options) {

    var intialComponent = ldba.initialComponent();
    var acceptingComponent = (Automaton<Object, ?>) ldba.acceptingComponent();

    HoaConsumerExtended<Object> hoa = new HoaConsumerExtended<>(consumer,
      acceptingComponent.factory().alphabet(), acceptingComponent.acceptance(),
      (Set<Object>) ldba.initialStates(), options, false, "LDBA");

    var patchedVisitor = new Automaton.HybridVisitor<S>() {
      @Override
      public void enter(S state) {
        hoa.visitor.enter(state);
      }

      @Override
      public void exit(S state) {
        ldba.epsilonJumps((S) state).forEach(hoa::addEpsilonEdge);
        ldba.valuationSetJumps((S) state).forEach((a, b) -> b.forEach(d -> hoa.addEdge(a, d)));
        hoa.visitor.exit(state);
      }

      @Override
      public void visitLabelledEdge(Edge<S> edge, ValuationSet valuationSet) {
        hoa.visitor.visitLabelledEdge(TypeUtil.cast(edge), valuationSet);
      }

      @Override
      public void visitEdge(Edge<S> edge, BitSet valuation) {
        hoa.visitor.visitEdge(TypeUtil.cast(edge), valuation);
      }
    };

    intialComponent.accept(patchedVisitor);
    acceptingComponent.accept(hoa.visitor);
    hoa.done();
  }

  public enum HoaOption {
    /**
     * Print annotations, e.g. state labels, if available
     */
    ANNOTATIONS,
    /**
     * Create one transition for each element of the AP-power-set instead of complex expressions
     * (which are not supported by all parsers).
     */
    SIMPLE_TRANSITION_LABELS
  }
}
