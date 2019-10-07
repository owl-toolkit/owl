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

package owl.automaton.transformations;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.run.Environment;
import owl.run.modules.OwlModule;

public final class ParityUtil {
  public static final OwlModule<OwlModule.Transformer> COMPLEMENT_MODULE = OwlModule.of(
    "complement-parity",
    "Complements a parity automaton",
    (commandLine, environment) -> input -> ParityUtil.complement(
      MutableAutomatonUtil.asMutable(AutomatonUtil.cast(input, ParityAcceptance.class)),
      new MutableAutomatonUtil.Sink()));

  public static final OwlModule<OwlModule.Transformer> CONVERSION_MODULE = OwlModule.of(
    "convert-parity",
    "Converts a parity automaton into the desired type",
    new Options()
      .addOptionGroup(new OptionGroup()
        .addOption(new Option(null, "max", false, null))
        .addOption(new Option(null, "min", false, null)))
      .addOptionGroup(new OptionGroup()
        .addOption(new Option(null, "even", false, null))
        .addOption(new Option(null, "odd", false, null))),
    ((CommandLine commandLine, Environment environment) -> {
      @Nullable
      Boolean toMax;
      if (commandLine.hasOption("max")) {
        toMax = Boolean.TRUE;
      } else if (commandLine.hasOption("min")) {
        toMax = Boolean.FALSE;
      } else {
        toMax = null;
      }

      @Nullable
      Boolean toEven;
      if (commandLine.hasOption("even")) {
        toEven = Boolean.TRUE;
      } else if (commandLine.hasOption("odd")) {
        toEven = Boolean.FALSE;
      } else {
        toEven = null;
      }

      return (Object input) -> {
        var automaton = AutomatonUtil.cast(input, ParityAcceptance.class);
        var target = automaton.acceptance().parity();

        if (toEven != null) {
          target = target.setEven(toEven);
        }

        if (toMax != null) {
          target = target.setMax(toMax);
        }

        return ParityUtil.convert(automaton, target, new MutableAutomatonUtil.Sink());
      };
    })
  );

  private ParityUtil() {
  }

  public static <S> MutableAutomaton<S, ParityAcceptance> complement(
    MutableAutomaton<S, ParityAcceptance> automaton, S sinkState) {
    // TODO Similarly exists in Views
    assert automaton.is(Automaton.Property.DETERMINISTIC);
    ParityAcceptance acceptance = automaton.acceptance();

    // Automaton currently accepts nothing
    if (acceptance.acceptanceSets() == 0 && !acceptance.emptyIsAccepting()) {
      var parityAcceptance = new ParityAcceptance(1, Parity.MIN_EVEN);
      var universalAutomaton = AutomatonFactory.singleton(automaton.factory(),
        sinkState, parityAcceptance, parityAcceptance.acceptingSet());
      return MutableAutomatonFactory.copy(universalAutomaton);
    }

    if (acceptance.acceptanceSets() <= 1) {
      acceptance = acceptance.withAcceptanceSets(2);
      automaton.acceptance(acceptance);
    }

    MutableAutomatonUtil.complete(automaton, sinkState);
    automaton.acceptance(acceptance.complement());
    return automaton;
  }

  public static <S> Automaton<S, ParityAcceptance> convert(Automaton<S, ParityAcceptance> automaton,
    Parity toParity, S sink) {

    // TODO Check for "colored" property
    if (automaton.acceptance().parity().equals(toParity)) {
      return automaton;
    }

    var mutableAutomaton = MutableAutomatonUtil.asMutable(automaton);
    // ensure that there is enough colours to have rejecting state.
    mutableAutomaton.updateAcceptance(x -> x.withAcceptanceSets(Math.max(3, x.acceptanceSets())));
    MutableAutomatonUtil.complete(mutableAutomaton, sink);

    ParityAcceptance fromAcceptance = mutableAutomaton.acceptance();
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
      mapping = i -> newAcceptanceSets - i;
    }

    var maximalNewAcceptance = new AtomicInteger(0);

    mutableAutomaton.updateEdges((state, edge) -> {
      if (!edge.hasAcceptanceSets()) {
        // TODO: should this throw?
        return edge;
      }

      int newAcceptance = mapping.applyAsInt(edge.smallestAcceptanceSet());

      if (newAcceptance == -1) {
        // TODO: should this throw?
        return edge.withoutAcceptance();
      }

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
