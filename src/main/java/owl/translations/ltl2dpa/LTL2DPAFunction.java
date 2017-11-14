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

import java.util.BitSet;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.transformations.ParityUtil;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;
import owl.translations.Optimisation;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.ldba2dpa.RankingAutomatonBuilder;
import owl.translations.ldba2dpa.RankingState;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.EquivalenceClassLanguageLattice;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;

public class LTL2DPAFunction
  implements Function<LabelledFormula, MutableAutomaton<?, ParityAcceptance>> {

  // Polling time in ms.
  private static final int SLEEP_MS = 50;
  private final boolean breakpointFree;
  private final EnumSet<Optimisation> optimisations;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>> translatorBreakpoint;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>> translatorBreakpointFree;

  public LTL2DPAFunction(EnumSet<Optimisation> optimisations) {
    this(optimisations, false);
  }

  public LTL2DPAFunction(EnumSet<Optimisation> optimisations, boolean breakpointFree) {
    this.optimisations = EnumSet.copyOf(optimisations);

    EnumSet<Optimisation> ldbaOptimisations = EnumSet.of(
      Optimisation.DETERMINISTIC_INITIAL_COMPONENT,
      Optimisation.EAGER_UNFOLD,
      Optimisation.SUPPRESS_JUMPS,
      Optimisation.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES);

    if (optimisations.contains(Optimisation.OPTIMISED_STATE_STRUCTURE)) {
      ldbaOptimisations.add(Optimisation.OPTIMISED_STATE_STRUCTURE);
    }

    translatorBreakpointFree = LTL2LDBAFunction.createDegeneralizedBreakpointFreeLDBABuilder(
      ldbaOptimisations);
    translatorBreakpoint = LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(
      ldbaOptimisations);
    this.breakpointFree = breakpointFree;
  }

  @Override
  public MutableAutomaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    if (!optimisations.contains(Optimisation.COMPLEMENT_CONSTRUCTION)) {
      // TODO Instead, one should use a direct executor here
      ComplementableAutomaton<?> automaton = apply(formula, new AtomicInteger());

      if (optimisations.contains(Optimisation.COMPLETE)) {
        automaton.complete();
      }

      return automaton.automaton;
    }

    // TODO Use CompletionService
    // TODO Use environment pool?
    ExecutorService executor = Executors.newFixedThreadPool(2);

    AtomicInteger automatonCounter = new AtomicInteger(-1);
    AtomicInteger complementCounter = new AtomicInteger(-1);

    Future<ComplementableAutomaton<?>> automatonFuture =
      executor.submit(() -> apply(formula, automatonCounter));
    Future<ComplementableAutomaton<?>> complementFuture =
      executor.submit(() -> apply(formula.not(), complementCounter));

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

            if (optimisations.contains(Optimisation.COMPLETE)) {
              automaton.complete();
            }

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

  private ComplementableAutomaton<?> apply(LabelledFormula formula, AtomicInteger sizeCounter) {
    return breakpointFree
      ? applyBreakpointFree(formula, sizeCounter)
      : applyBreakpoint(formula, sizeCounter);
  }

  private ComplementableAutomaton<?> applyBreakpoint(LabelledFormula formula,
    AtomicInteger counter) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointState,
      BuchiAcceptance, GObligations> ldba = translatorBreakpoint.apply(formula);

    if (ldba.isDeterministic()) {
      return new ComplementableAutomaton<>(ParityUtil.viewAsParity(
        (MutableAutomaton<DegeneralizedBreakpointState, BuchiAcceptance>)
          ldba.getAcceptingComponent()), DegeneralizedBreakpointState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    LanguageLattice<EquivalenceClass, DegeneralizedBreakpointState, GObligations> oracle =
      new EquivalenceClassLanguageLattice(
        ldba.getInitialComponent().getInitialState().getFactory());
    RankingAutomatonBuilder<EquivalenceClass, DegeneralizedBreakpointState, GObligations,
      EquivalenceClass> builder = new RankingAutomatonBuilder<>(ldba, counter, optimisations,
      oracle, this::hasSafetyCore, true);
    builder.add(ldba.getInitialComponent().getInitialState());
    return new ComplementableAutomaton<>(builder.build(), RankingState::createSink);
  }

  private ComplementableAutomaton<?>
  applyBreakpointFree(LabelledFormula formula, AtomicInteger counter) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointFreeState,
      BuchiAcceptance, FGObligations> ldba = translatorBreakpointFree.apply(formula);

    if (ldba.isDeterministic()) {
      return new ComplementableAutomaton<>(ParityUtil.viewAsParity(
        (MutableAutomaton<DegeneralizedBreakpointFreeState, BuchiAcceptance>) ldba
          .getAcceptingComponent()), DegeneralizedBreakpointFreeState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    RankingAutomatonBuilder<EquivalenceClass, DegeneralizedBreakpointFreeState, FGObligations,
      Void> builder = new RankingAutomatonBuilder<>(ldba, counter, optimisations,
      new BooleanLattice(), this::hasSafetyCore, true);
    builder.add(ldba.getInitialComponent().getInitialState());
    return new ComplementableAutomaton<>(builder.build(), RankingState::createSink);
  }

  private boolean hasSafetyCore(EquivalenceClass state) {
    if (state.testSupport(Fragments::isSafety)) {
      return true;
    }

    // Check if the state has an independent safety core.
    if (optimisations.contains(Optimisation.EXISTS_SAFETY_CORE)) {
      BitSet nonSafetyAP = Collector.collectAtoms(state.getSupport(x -> !Fragments.isSafety(x)));

      EquivalenceClass core = state.substitute(x -> {
        if (!Fragments.isSafety(x)) {
          return BooleanConstant.FALSE;
        }

        BitSet ap = Collector.collectAtoms(x);
        assert !ap.isEmpty() : "Formula " + x + " has empty AP.";
        ap.and(nonSafetyAP);
        return ap.isEmpty() ? BooleanConstant.TRUE : x;
      });

      if (core.isTrue()) {
        return true;
      }

      core.free();
    }

    return false;
  }

  static final class ComplementableAutomaton<T> {
    final MutableAutomaton<T, ParityAcceptance> automaton;
    final Supplier<T> sinkSupplier;

    ComplementableAutomaton(MutableAutomaton<T, ParityAcceptance> automaton,
      Supplier<T> sinkSupplier) {
      this.automaton = automaton;
      this.sinkSupplier = sinkSupplier;
    }

    void complete() {
      BitSet reject = new BitSet();
      reject.set(0);
      AutomatonUtil.complete(automaton, sinkSupplier, () -> reject);
    }

    void complement() {
      ParityUtil.complement(automaton, sinkSupplier);
    }
  }
}
