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

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLETE;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.Uninterruptibles;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import owl.automaton.Automaton;
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
import owl.run.env.Environment;
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

public class LTL2DPAFunction implements Function<LabelledFormula, Automaton<?, ParityAcceptance>> {

  // Polling time in ms.
  private static final int SLEEP_MS = 10;
  private final boolean breakpointFree;
  private final EnumSet<Configuration> configuration;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>> translatorBreakpoint;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>> translatorBreakpointFree;

  public LTL2DPAFunction(Environment env) {
    this(env, EnumSet.allOf(Configuration.class), false);
  }

  public LTL2DPAFunction(Environment env, EnumSet<Configuration> configuration) {
    this(env, configuration, false);
  }

  public LTL2DPAFunction(Environment env, EnumSet<Configuration> configuration,
    boolean guessF) {
    this.configuration = EnumSet.copyOf(configuration);

    EnumSet<LTL2LDBAFunction.Configuration> ldbaConfiguration = EnumSet.of(
      LTL2LDBAFunction.Configuration.EAGER_UNFOLD,
      LTL2LDBAFunction.Configuration.EPSILON_TRANSITIONS,
      LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS);

    if (configuration.contains(Configuration.OPTIMISED_STATE_STRUCTURE)) {
      ldbaConfiguration.add(LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE);
    }

    translatorBreakpointFree =
      LTL2LDBAFunction.createDegeneralizedBreakpointFreeLDBABuilder(env, ldbaConfiguration);
    translatorBreakpoint =
      LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(env, ldbaConfiguration);
    this.breakpointFree = guessF;
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    ExecutorService executor = Executors.newCachedThreadPool();

    Automaton<?, ParityAcceptance> automaton = null;
    Automaton<?, ParityAcceptance> complement = null;

    AtomicInteger automatonSize = new AtomicInteger(-1);
    AtomicInteger complementSize = new AtomicInteger(-1);

    Future<Result<?>> automatonFuture = executor.submit(() -> apply(formula, automatonSize));
    Future<Result<?>> complementFuture = executor.submit(() -> configuration.contains(
      COMPLEMENT_CONSTRUCTION) ? apply(formula.not(), complementSize) : null);

    boolean automatonDone = false;
    boolean complementDone = false;

    try {
      while (!automatonDone || !complementDone) {
        // Retrieve done futures.
        if (!automatonDone && automatonFuture.isDone()) {
          Result<?> result = Futures.getDone(automatonFuture);

          if (configuration.contains(COMPLETE)) {
            result.complete();
          }

          automaton = result.automaton;
          automatonDone = true;
          automatonSize.set(automaton.size());
        }

        if (!complementDone && complementFuture.isDone()) {
          Result<?> result = Futures.getDone(complementFuture);

          if (result != null) {
            result.complement();
          }

          complement = result != null ? result.automaton : null;
          complementDone = true;
          complementSize.set(complement != null ? complement.size() : Integer.MAX_VALUE);
        }

        // Cancel too large futures.
        if (automatonDone && automatonSize.get() < complementSize.get()) {
          complementDone = true;
          complementFuture.cancel(true);
        }

        if (complementDone && complementSize.get() < automatonSize.get()) {
          automatonDone = true;
          automatonFuture.cancel(true);
        }

        if (automatonDone && complementDone) {
          break;
        }

        Uninterruptibles.sleepUninterruptibly(SLEEP_MS, TimeUnit.MILLISECONDS);
      }

      if (complement == null) {
        assert automaton != null;
        return automaton;
      }

      if (automaton == null) {
        return complement;
      }

      return automaton.size() <= complement.size() ? automaton : complement;
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

  private Result<?> apply(LabelledFormula formula, AtomicInteger size) {
    return breakpointFree ? applyBreakpointFree(formula, size) : applyBreakpoint(formula, size);
  }

  private Result<?> applyBreakpoint(LabelledFormula formula, AtomicInteger size) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointState,
      BuchiAcceptance, GObligations> ldba = translatorBreakpoint.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(ParityUtil.viewAsParity(
        (MutableAutomaton<DegeneralizedBreakpointState, BuchiAcceptance>)
          ldba.getAcceptingComponent()), DegeneralizedBreakpointState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    LanguageLattice<EquivalenceClass, DegeneralizedBreakpointState, GObligations> oracle =
      new EquivalenceClassLanguageLattice(
        ldba.getInitialComponent().getInitialState().getFactory());
    RankingAutomatonBuilder<EquivalenceClass, DegeneralizedBreakpointState, GObligations,
      EquivalenceClass> builder = new RankingAutomatonBuilder<>(ldba, size, true,
      oracle, this::hasSafetyCore, true);
    builder.add(ldba.getInitialComponent().getInitialState());
    return new Result<>(builder.build(), RankingState::of);
  }

  private Result<?> applyBreakpointFree(LabelledFormula formula, AtomicInteger size) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointFreeState,
      BuchiAcceptance, FGObligations> ldba = translatorBreakpointFree.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(ParityUtil.viewAsParity(
        (MutableAutomaton<DegeneralizedBreakpointFreeState, BuchiAcceptance>) ldba
          .getAcceptingComponent()), DegeneralizedBreakpointFreeState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    RankingAutomatonBuilder<EquivalenceClass, DegeneralizedBreakpointFreeState, FGObligations, Void>
      builder = new RankingAutomatonBuilder<>(ldba, size, true,
      new BooleanLattice(), this::hasSafetyCore, true);
    builder.add(ldba.getInitialComponent().getInitialState());
    return new Result<>(builder.build(), RankingState::of);
  }

  private boolean hasSafetyCore(EquivalenceClass state) {
    if (state.testSupport(Fragments::isSafety)) {
      return true;
    }

    // Check if the state has an independent safety core.
    if (configuration.contains(Configuration.EXISTS_SAFETY_CORE)) {
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

  static final class Result<T> {
    final MutableAutomaton<T, ParityAcceptance> automaton;
    final Supplier<T> sinkSupplier;

    Result(MutableAutomaton<T, ParityAcceptance> automaton, Supplier<T> sinkSupplier) {
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

  public enum Configuration {
    OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE, COMPLETE
  }
}
