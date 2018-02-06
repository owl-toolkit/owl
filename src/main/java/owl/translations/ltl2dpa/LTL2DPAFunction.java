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

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COLOUR_OVERAPPROXIMATION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLETE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GREEDY;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Uninterruptibles;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.StreamingAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.transformations.ParityUtil;
import owl.collections.Collections3;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;
import owl.run.Environment;
import owl.translations.ldba2dpa.FlatRankingAutomaton;
import owl.translations.ldba2dpa.FlatRankingState;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.EquivalenceClassLanguageLattice;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;
import owl.util.DaemonThreadFactory;

public class LTL2DPAFunction implements Function<LabelledFormula, Automaton<?, ParityAcceptance>> {
  public static final Set<Configuration> RECOMMENDED_ASYMMETRIC_CONFIG = Set.of(
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE);

  public static final Set<Configuration> RECOMMENDED_SYMMETRIC_CONFIG = Set.of(GUESS_F,
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE);

  private static final int GREEDY_TIME_MS = 10;

  private final EnumSet<Configuration> configuration;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>> translatorBreakpoint;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>> translatorBreakpointFree;

  public LTL2DPAFunction(Environment env, Set<Configuration> configuration) {
    this.configuration = EnumSet.copyOf(configuration);
    Preconditions.checkArgument(!configuration.contains(GREEDY)
      || configuration.contains(COMPLEMENT_CONSTRUCTION),
      "GREEDY requires COMPLEMENT_CONSTRUCTION");

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
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    ExecutorService executor = Executors.newCachedThreadPool(
      new DaemonThreadFactory(Thread.currentThread().getThreadGroup()));
    Future<Result<?>> automatonFuture = executor.submit(callable(formula, false));
    Future<Result<?>> complementFuture = executor.submit(callable(formula, true));

    try {
      Automaton<?, ParityAcceptance> automaton = null;
      Automaton<?, ParityAcceptance> complement = null;

      do {
        if (automaton == null) {
          automaton = getAutomaton(automatonFuture);
        }

        if (complement == null) {
          complement = getComplement(complementFuture);
        }
      } while (!exitLoop(automaton, complement));

      if (complement == null) {
        assert automaton != null;
        return automaton;
      }

      if (automaton == null) {
        return complement;
      }

      // Select smaller automaton.
      if (automaton.size() < complement.size()) {
        return automaton;
      }

      if (automaton.size() > complement.size()) {
        return complement;
      }

      return automaton.getAcceptance().getAcceptanceSets()
        <= complement.getAcceptance().getAcceptanceSets() ? automaton : complement;
    } catch (ExecutionException ex) {
      // The translation broke down, it is unsafe to continue...
      automatonFuture.cancel(true);
      complementFuture.cancel(true);
      //noinspection ProhibitedExceptionThrown
      throw new RuntimeException(ex); // NOPMD
    } finally {
      executor.shutdown();
    }
  }

  private boolean exitLoop(@Nullable Automaton<?, ?> automaton,
    @Nullable Automaton<?, ?> complement) {
    if (configuration.contains(GREEDY)) {
      return automaton != null || complement != null;
    }

    if (configuration.contains(COMPLEMENT_CONSTRUCTION)) {
      return  automaton != null && complement != null;
    }

    return automaton != null;
  }

  @Nullable
  private Result<?> getResult(Future<Result<?>> future) throws ExecutionException {
    if (!configuration.contains(GREEDY)) {
      return Uninterruptibles.getUninterruptibly(future);
    }

    try {
      return future.get(GREEDY_TIME_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      // Swallow exception
      return null;
    }
  }

  @Nullable
  private Automaton<?, ParityAcceptance> getAutomaton(Future<Result<?>> future)
    throws ExecutionException {
    Result<?> result = getResult(future);

    if (result == null) {
      return null;
    }

    if (configuration.contains(COMPLETE)) {
      return result.complete();
    } else {
      return result.automaton;
    }
  }

  @Nullable
  private Automaton<?, ParityAcceptance> getComplement(Future<Result<?>> future)
    throws ExecutionException {
    Result<?> result = getResult(future);
    return result != null ? result.complement() : null;
  }

  private Callable<Result<?>> callable(LabelledFormula formula, boolean complement) {
    if (!complement) {
      return configuration.contains(GUESS_F)
        ? () -> applyBreakpointFree(formula)
        : () -> applyBreakpoint(formula);
    }

    if (configuration.contains(COMPLEMENT_CONSTRUCTION)) {
      return configuration.contains(GUESS_F)
        ? () -> applyBreakpointFree(formula.not())
        : () -> applyBreakpoint(formula.not());
    }

    return () -> null;
  }

  private Result<?> applyBreakpoint(LabelledFormula formula) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointState,
      BuchiAcceptance, GObligations> ldba = translatorBreakpoint.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(Views.viewAs(ldba.getAcceptingComponent(), ParityAcceptance.class),
        DegeneralizedBreakpointState.createSink(), -1);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    LanguageLattice<DegeneralizedBreakpointState, GObligations, EquivalenceClass> oracle =
      new EquivalenceClassLanguageLattice(
        ldba.getInitialComponent().getInitialState().getFactory());

    Automaton<FlatRankingState<EquivalenceClass, DegeneralizedBreakpointState>, ParityAcceptance>
      automaton = FlatRankingAutomaton.of(ldba, oracle, this::hasSafetyCore, true,
      configuration.contains(OPTIMISE_INITIAL_STATE));
    return new Result<>(automaton, FlatRankingState.of(), 2 * ldba.getAcceptingComponent().size());
  }

  private Result<?> applyBreakpointFree(LabelledFormula formula) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointFreeState,
      BuchiAcceptance, FGObligations> ldba = translatorBreakpointFree.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(Views.viewAs(ldba.getAcceptingComponent(), ParityAcceptance.class),
        DegeneralizedBreakpointFreeState.createSink(), -1);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    Automaton<FlatRankingState<EquivalenceClass, DegeneralizedBreakpointFreeState>,
      ParityAcceptance> automaton = FlatRankingAutomaton.of(ldba, new BooleanLattice(),
      this::hasSafetyCore, true, configuration.contains(OPTIMISE_INITIAL_STATE));
    return new Result<>(automaton, FlatRankingState.of(), 2 * ldba.getAcceptingComponent().size());
  }

  private boolean hasSafetyCore(EquivalenceClass state) {
    if (state.testSupport(Fragments::isSafety)) {
      return true;
    }

    // Check if the state has an independent safety core.
    if (configuration.contains(Configuration.EXISTS_SAFETY_CORE)) {
      BitSet nonSafety = Collector.collectAtoms(state.getSupport(x -> !Fragments.isSafety(x)));

      EquivalenceClass core = state.substitute(x -> {
        if (!Fragments.isSafety(x)) {
          return BooleanConstant.FALSE;
        }

        BitSet ap = Collector.collectAtoms(x);
        assert !ap.isEmpty() : "Formula " + x + " has empty AP.";
        return Collections3.isDisjointConsuming(ap, nonSafety) ? BooleanConstant.TRUE : x;
      });

      return core.isTrue();
    }

    return false;
  }

  public enum Configuration {
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE,
    COMPLETE, GUESS_F, COLOUR_OVERAPPROXIMATION, GREEDY
  }

  final class Result<T> {
    final Automaton<T, ParityAcceptance> automaton;
    final T sinkState;

    Result(Automaton<T, ParityAcceptance> automaton, T sinkState,
      int colourApproximation) {
      this.automaton = automaton;
      this.sinkState = sinkState;

      // HACK: Query state space to initialise colour correctly.
      if (automaton instanceof StreamingAutomaton) {
        if (configuration.contains(COLOUR_OVERAPPROXIMATION)) {
          automaton.getAcceptance().setAcceptanceSets(colourApproximation + 2);
        } else {
          automaton.getStates();
        }
      }
    }

    Automaton<T, ParityAcceptance> complete() {
      MutableAutomaton<T, ParityAcceptance> automaton = AutomatonUtil.asMutable(this.automaton);
      BitSet reject = new BitSet();
      reject.set(0);
      AutomatonUtil.complete(automaton, sinkState, reject);
      return automaton;
    }

    Automaton<T, ParityAcceptance> complement() {
      MutableAutomaton<T, ParityAcceptance> automaton = AutomatonUtil.asMutable(this.automaton);
      ParityUtil.complement(automaton, sinkState);
      return automaton;
    }
  }
}
