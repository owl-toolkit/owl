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
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.Formula;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.AcceptingComponent;
import owl.translations.ltl2ldba.InitialComponent;
import owl.translations.ltl2ldba.InitialComponentState;
import owl.translations.ltl2ldba.LTL2LDBA;
import owl.translations.ltl2ldba.RecurringObligations;

public class Ltl2Dpa implements Function<Formula, ParityAutomaton<?>> {

  // Polling time in ms.
  private static final int SLEEP_MS = 50;
  private final EnumSet<Optimisation> optimisations;
  private final LTL2LDBA translator;

  public Ltl2Dpa() {
    this(EnumSet.complementOf(EnumSet.of(Optimisation.PARALLEL)));
  }

  public Ltl2Dpa(EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    this.optimisations.remove(Optimisation.FORCE_JUMPS);
    translator = new LTL2LDBA(this.optimisations);
  }

  @Override
  public ParityAutomaton<?> apply(Formula formula) {
    if (!optimisations.contains(Optimisation.PARALLEL)) {
      return apply(formula, new AtomicInteger());
    }

    ExecutorService executor = Executors.newFixedThreadPool(2);

    AtomicInteger automatonCounter = new AtomicInteger(-1);
    AtomicInteger complementCounter = new AtomicInteger(-1);

    Future<ParityAutomaton<?>> automatonFuture = executor
      .submit(() -> apply(formula, automatonCounter));
    Future<ParityAutomaton<?>> complementFuture = executor
      .submit(() -> apply(formula.not(), complementCounter));

    ParityAutomaton<?> automaton = null;
    ParityAutomaton<?> complement = null;

    try {
      while (true) {
        try {
          // Get new results.
          if (automaton == null && automatonFuture.isDone()) {
            automaton = automatonFuture.get();
          }

          if (complement == null && complementFuture.isDone()) {
            complement = complementFuture.get();
          }

          int size = automaton == null ? automatonCounter.get() : automaton.size();
          int complementSize = complement == null ? complementCounter.get() : complement.size();

          if (automaton != null && size <= complementSize) {
            complementFuture.cancel(true);
            return automaton;
          }

          if (complement != null && complementSize < size) {
            automatonFuture.cancel(true);
            complement.complement();
            return complement;
          }

          Thread.sleep(SLEEP_MS);
        } catch (InterruptedException ex) {
          // Let's continue checking stuff...
        }
      }
    } catch (ExecutionException ex) {
      // The translation broke down, it is unsafe to continue...
      // In order to immediately shutdown the JVM without using SYSTEM.exit(), we cancel all running
      // Futures.

      automatonFuture.cancel(true);
      complementFuture.cancel(true);
      throw new RuntimeException(ex); // NOPMD
    } finally {
      executor.shutdown();
    }
  }

  private ParityAutomaton<?> apply(Formula formula, AtomicInteger size) {
    LimitDeterministicAutomaton<InitialComponentState, AcceptingComponent.State, BuchiAcceptance,
      InitialComponent<AcceptingComponent.State, RecurringObligations>, AcceptingComponent> ldba =
      translator.apply(formula);

    if (ldba.isDeterministic()) {
      return new WrappedParityAutomaton(ldba.getAcceptingComponent());
    }

    RankingParityAutomaton parity = new RankingParityAutomaton(ldba, size, optimisations);
    parity.generate();

    return parity;
  }
}
