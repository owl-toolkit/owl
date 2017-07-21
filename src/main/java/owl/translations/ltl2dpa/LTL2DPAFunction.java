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

package owl.translations.ltl2dpa;

import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.transformations.ParityAutomatonUtil;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.GObligations;

public final class LTL2DPAFunction implements Function<Formula, Automaton<?, ParityAcceptance>> {

  // Polling time in ms.
  private static final int SLEEP_MS = 50;
  private final EnumSet<Optimisation> optimisations;
  private final Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>> translator;

  public LTL2DPAFunction() {
    this(EnumSet.complementOf(EnumSet.of(Optimisation.PARALLEL)));
  }

  public LTL2DPAFunction(EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    this.optimisations.remove(Optimisation.FORCE_JUMPS);
    translator = LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(this.optimisations);
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(Formula formula) {
    if (!optimisations.contains(Optimisation.PARALLEL)) {
      // TODO Instead, one should use a direct executor here
      return apply(formula, new AtomicInteger()).automaton;
    }

    ExecutorService executor = Executors.newFixedThreadPool(2);

    AtomicInteger automatonCounter = new AtomicInteger(-1);
    AtomicInteger complementCounter = new AtomicInteger(-1);

    Future<ComplementableAutomaton<?>> automatonFuture = executor
      .submit(() -> apply(formula, automatonCounter));
    Future<ComplementableAutomaton<?>> complementFuture = executor
      .submit(() -> apply(formula.not(), complementCounter));

    try {
      ComplementableAutomaton<?> complement = null;
      ComplementableAutomaton<?> automaton = null;
      while (true) {
        //noinspection NestedTryStatement
        try {
          // Get new results.
          if (automaton == null && automatonFuture.isDone()) {
            automaton = automatonFuture.get();
          }

          if (complement == null && complementFuture.isDone()) {
            complement = complementFuture.get();
          }

          int size = automaton == null
                     ? automatonCounter.get()
                     : automaton.automaton.stateCount();
          int complementSize = complement == null
                               ? complementCounter.get()
                               : complement.automaton.stateCount();

          if (automaton != null && size <= complementSize) {
            complementFuture.cancel(true);
            return automaton.automaton;
          }

          if (complement != null && complementSize < size) {
            automatonFuture.cancel(true);
            complement.complement();
            return complement.automaton;
          }

          //noinspection BusyWait
          Thread.sleep(SLEEP_MS);
        } catch (InterruptedException ignored) {
          // Let's continue checking stuff...
        }
      }
    } catch (ExecutionException ex) {
      // The translation broke down, it is unsafe to continue...
      // In order to immediately shutdown the JVM without using SYSTEM.exit(), we cancel all running
      // Futures.

      automatonFuture.cancel(true);
      complementFuture.cancel(true);
      //noinspection ProhibitedExceptionThrown
      throw new RuntimeException(ex); // NOPMD
    } finally {
      executor.shutdown();
    }
  }

  private ComplementableAutomaton<?> apply(Formula formula, AtomicInteger sizeCounter) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointState, BuchiAcceptance,
      GObligations> ldba = translator.apply(formula);

    if (ldba.isDeterministic()) {
      return new ComplementableAutomaton<>(ParityAutomatonUtil.changeAcceptance(
        (MutableAutomaton<DegeneralizedBreakpointState, BuchiAcceptance>)
          ldba.getAcceptingComponent()), DegeneralizedBreakpointState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    RankingAutomatonBuilder builder = RankingAutomatonBuilder
      .create(ldba, sizeCounter, optimisations, ldba.getAcceptingComponent().getFactory());
    builder.add(ldba.getInitialComponent().getInitialState());
    return new ComplementableAutomaton<>(builder.build(), RankingState::createSink);
  }

  static final class ComplementableAutomaton<T> {
    final MutableAutomaton<T, ParityAcceptance> automaton;
    final Supplier<T> sinkSupplier;

    ComplementableAutomaton(MutableAutomaton<T, ParityAcceptance> automaton,
      Supplier<T> sinkSupplier) {
      this.automaton = automaton;
      this.sinkSupplier = sinkSupplier;
    }

    void complement() {
      ParityAutomatonUtil.complement(automaton, sinkSupplier);
    }
  }
}
