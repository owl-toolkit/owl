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

package owl.automaton;

import com.google.common.base.Preconditions;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;

public final class ParityUtil {

  private ParityUtil() {}

  public static <S> Automaton<S, ? extends ParityAcceptance> convert(
    Automaton<S, ? extends ParityAcceptance> automaton, Parity toParity) {

    if (automaton.acceptance().parity() == toParity) {
      return automaton;
    }

    var mutableAutomaton
      = (HashMapAutomaton<S, ParityAcceptance>) HashMapAutomaton.copyOf(automaton);
    Preconditions.checkArgument(mutableAutomaton.is(Automaton.Property.COMPLETE));

    // Ensure that there are enough colours to have a rejecting state.
    mutableAutomaton.updateAcceptance(x -> x.withAcceptanceSets(Math.max(3, x.acceptanceSets())));

    ParityAcceptance fromAcceptance = mutableAutomaton.acceptance();

    // Ensure automaton is coloured.
    if (fromAcceptance.parity().max()) {
      int colours = fromAcceptance.acceptanceSets();
      mutableAutomaton.acceptance(fromAcceptance.withAcceptanceSets(colours + 2));
      mutableAutomaton.updateEdges((state, edge) ->
        edge.withAcceptance(edge.colours().last().orElse(-1) + 2));
    } else {
      int colours = fromAcceptance.acceptanceSets();
      mutableAutomaton.acceptance(fromAcceptance.withAcceptanceSets(colours + 2));
      mutableAutomaton.updateEdges((state, edge) ->
        edge.withAcceptance(edge.colours().first().orElse(colours - 2) + 2));
    }

    fromAcceptance = mutableAutomaton.acceptance();
    IntUnaryOperator mapping;

    if (fromAcceptance.parity().max() == toParity.max()) {
      assert fromAcceptance.parity().even() != toParity.even();
      mapping = i -> i + 1;
    } else {
      int acceptanceSets = fromAcceptance.acceptanceSets();
      int leastImportantColor = fromAcceptance.parity().max() ? 0 : acceptanceSets - 1;
      int offset;

      if (fromAcceptance.parity().even() == toParity.even()) {
        offset = fromAcceptance.isAccepting(leastImportantColor) ? 0 : 1;
      } else {
        // Delete the least important color
        offset = fromAcceptance.isAccepting(leastImportantColor) ? -1 : -2;
      }

      int newAcceptanceSets = acceptanceSets + offset;
      //noinspection UnusedAssignment
      mapping = i -> newAcceptanceSets - i; // NOPMD

      throw new UnsupportedOperationException(
        "This combination of options is (currently) unsupported.");
    }

    var maximalNewAcceptance = new AtomicInteger(0);

    mutableAutomaton.updateEdges((state, edge) -> {
      int newAcceptance = mapping.applyAsInt(edge.colours().first().orElseThrow());

      if (maximalNewAcceptance.get() < newAcceptance) {
        maximalNewAcceptance.set(newAcceptance);
      }

      return edge.withAcceptance(newAcceptance);
    });

    mutableAutomaton.trim();
    mutableAutomaton.acceptance(new ParityAcceptance(maximalNewAcceptance.get() + 1, toParity));
    return mutableAutomaton;
  }

}
