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
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import owl.translations.ldba2dpa.RankingAutomaton;
import owl.translations.ldba2dpa.RankingState;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.EquivalenceClassLanguageLattice;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;

public class LTL2DPAFunction implements Function<LabelledFormula, Automaton<?, ParityAcceptance>> {

  public static final Set<Configuration> RECOMMENDED_ASYMMETRIC_CONFIG = Set.of(
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE);

  public static final Set<Configuration> RECOMMENDED_SYMMETRIC_CONFIG = Set.of(GUESS_F,
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE);

  private final EnumSet<Configuration> configuration;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>> translatorBreakpoint;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>> translatorBreakpointFree;

  public LTL2DPAFunction(Environment env, Set<Configuration> configuration) {
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
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    ExecutorService executor = Executors.newCachedThreadPool();
    Future<Result<?>> automatonFuture = executor.submit(callable(formula, false));
    Future<Result<?>> complementFuture = executor.submit(callable(formula, true));

    try {
      Automaton<?, ParityAcceptance> automaton;
      Automaton<?, ParityAcceptance> complement;

      // Get automaton.
      Result<?> result = Uninterruptibles.getUninterruptibly(automatonFuture);

      if (configuration.contains(COMPLETE)) {
        automaton = result.complete();
      } else {
        automaton = result.automaton;
      }

      // Get complement automaton.
      result = Uninterruptibles.getUninterruptibly(complementFuture);

      if (result == null) {
        return automaton;
      }

      complement = result.complement();

      // Select smaller automaton.
      int size = automaton.size();

      if (size < complement.size()) {
        return automaton;
      }

      if (size > complement.size()) {
        return complement;
      }

      return automaton.getAcceptance().getAcceptanceSets()
               <= complement.getAcceptance().getAcceptanceSets() ? automaton : complement;
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
      return new Result<>(ParityUtil.viewAsParity(ldba.getAcceptingComponent()),
        DegeneralizedBreakpointState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    LanguageLattice<EquivalenceClass, DegeneralizedBreakpointState, GObligations> oracle =
      new EquivalenceClassLanguageLattice(
        ldba.getInitialComponent().getInitialState().getFactory());

    Automaton<RankingState<EquivalenceClass, DegeneralizedBreakpointState>, ParityAcceptance>
      automaton = RankingAutomaton.of(ldba, true, oracle, this::hasSafetyCore,
      true, configuration.contains(OPTIMISE_INITIAL_STATE));
    return new Result<>(automaton, RankingState::of);
  }

  private Result<?> applyBreakpointFree(LabelledFormula formula) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointFreeState,
      BuchiAcceptance, FGObligations> ldba = translatorBreakpointFree.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(ParityUtil.viewAsParity(ldba.getAcceptingComponent()),
        DegeneralizedBreakpointFreeState::createSink);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    Automaton<RankingState<EquivalenceClass, DegeneralizedBreakpointFreeState>, ParityAcceptance>
      automaton = RankingAutomaton.of(ldba, true, new BooleanLattice(),
      this::hasSafetyCore, true, configuration.contains(OPTIMISE_INITIAL_STATE));
    return new Result<>(automaton, RankingState::of);
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
    final Automaton<T, ParityAcceptance> automaton;
    final Supplier<T> sinkSupplier;

    Result(Automaton<T, ParityAcceptance> automaton, Supplier<T> sinkSupplier) {
      this.automaton = automaton;
      this.sinkSupplier = sinkSupplier;

      // HACK: Query state space to initialise colour correctly.
      automaton.getStates();
    }

    Automaton<T, ParityAcceptance> complete() {
      MutableAutomaton<T, ParityAcceptance> automaton = AutomatonUtil.asMutable(this.automaton);
      BitSet reject = new BitSet();
      reject.set(0);
      AutomatonUtil.complete(automaton, sinkSupplier, () -> reject);
      return automaton;
    }

    Automaton<T, ParityAcceptance> complement() {
      MutableAutomaton<T, ParityAcceptance> automaton = AutomatonUtil.asMutable(this.automaton);
      ParityUtil.complement(automaton, sinkSupplier);
      return automaton;
    }
  }

  public enum Configuration {
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE,
    COMPLETE, GUESS_F
  }
}
