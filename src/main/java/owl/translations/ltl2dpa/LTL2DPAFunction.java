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

import static com.google.common.base.Preconditions.checkArgument;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLETE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GREEDY;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.transformations.ParityUtil;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierFactory;
import owl.run.Environment;
import owl.translations.ldba2dpa.FlatRankingAutomaton;
import owl.translations.ldba2dpa.FlatRankingState;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.SafetyDetector;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.EquivalenceClassLanguageLattice;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;
import owl.util.DaemonThreadFactory;

public class LTL2DPAFunction implements Function<LabelledFormula, Automaton<?, ParityAcceptance>> {
  public static final Set<Configuration> RECOMMENDED_ASYMMETRIC_CONFIG = Set.of(
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE,
    COMPRESS_COLOURS);

  public static final Set<Configuration> RECOMMENDED_SYMMETRIC_CONFIG = Set.of(GUESS_F,
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE,
    COMPRESS_COLOURS);

  private static final int GREEDY_WAITING_TIME_SEC = 7;

  private final EnumSet<Configuration> configuration;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>> translatorBreakpoint;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>> translatorBreakpointFree;

  public LTL2DPAFunction(Environment env, Set<Configuration> configuration) {
    this.configuration = EnumSet.copyOf(configuration);
    checkArgument(!configuration.contains(GREEDY)
        || configuration.contains(COMPLEMENT_CONSTRUCTION),
      "GREEDY requires COMPLEMENT_CONSTRUCTION");

    EnumSet<LTL2LDBAFunction.Configuration> ldbaConfiguration = EnumSet.of(
      LTL2LDBAFunction.Configuration.EAGER_UNFOLD,
      LTL2LDBAFunction.Configuration.EPSILON_TRANSITIONS,
      LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS);

    if (configuration.contains(OPTIMISED_STATE_STRUCTURE)) {
      ldbaConfiguration.add(LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE); // NOPMD
    }

    translatorBreakpointFree =
      LTL2LDBAFunction.createDegeneralizedBreakpointFreeLDBABuilder(env, ldbaConfiguration);
    translatorBreakpoint =
      LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(env, ldbaConfiguration);
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    LabelledFormula formula2 = SimplifierFactory
      .apply(formula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT);

    var executor = Executors.newCachedThreadPool(
      new DaemonThreadFactory(Thread.currentThread().getThreadGroup()));
    var automatonFuture = executor.submit(callable(formula2, false));
    var complementFuture = executor.submit(callable(formula2, true));

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

      if (configuration.contains(GREEDY)) {
        return (formula2.formula() instanceof Conjunction) ? complement : automaton;
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
      return automaton != null && complement != null;
    }

    return automaton != null;
  }

  @Nullable
  private Result<?> getResult(Future<Result<?>> future) throws ExecutionException {
    if (!configuration.contains(GREEDY)) {
      return Uninterruptibles.getUninterruptibly(future);
    }

    try {
      return future.get(GREEDY_WAITING_TIME_SEC, TimeUnit.SECONDS);
    } catch (InterruptedException | TimeoutException e) {
      // Swallow exception
      return null;
    }
  }

  @Nullable
  private Automaton<?, ParityAcceptance> getAutomaton(Future<Result<?>> future)
    throws ExecutionException {
    var result = getResult(future);

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
    var result = getResult(future);
    return result == null ? null : result.complement();
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
    var ldba = translatorBreakpoint.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(Views.viewAs(ldba.getAcceptingComponent(), ParityAcceptance.class),
        DegeneralizedBreakpointState.createSink(), configuration.contains(COMPRESS_COLOURS));
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    var factory = ldba.getInitialComponent().getInitialState().factory();
    var automaton = FlatRankingAutomaton.of(ldba,
      new EquivalenceClassLanguageLattice(factory),
      x -> SafetyDetector.hasSafetyCore(x, configuration.contains(EXISTS_SAFETY_CORE)),
      true,
      configuration.contains(OPTIMISE_INITIAL_STATE));

    return new Result<>(automaton, FlatRankingState.of(factory.getFalse()),
      configuration.contains(COMPRESS_COLOURS));
  }

  private Result<?> applyBreakpointFree(LabelledFormula formula) {
    var ldba = translatorBreakpointFree.apply(formula);

    if (ldba.isDeterministic()) {
      return new Result<>(Views.viewAs(ldba.getAcceptingComponent(), ParityAcceptance.class),
        DegeneralizedBreakpointFreeState.createSink(), configuration.contains(COMPRESS_COLOURS));
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    var factory = ldba.getInitialComponent().getInitialState().factory();
    var automaton = FlatRankingAutomaton.of(ldba,
      new BooleanLattice(),
      x -> SafetyDetector.hasSafetyCore(x, configuration.contains(EXISTS_SAFETY_CORE)),
      true,
      configuration.contains(OPTIMISE_INITIAL_STATE));

    return new Result<>(automaton, FlatRankingState.of(factory.getFalse()),
      configuration.contains(COMPRESS_COLOURS));
  }

  public enum Configuration {
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE,
    COMPLETE, GUESS_F, GREEDY, COMPRESS_COLOURS
  }

  private static final class Result<T> {
    final Automaton<T, ParityAcceptance> automaton;
    final T sinkState;

    Result(Automaton<T, ParityAcceptance> automaton, T sinkState, boolean compressColours) {
      this.sinkState = sinkState;

      if (compressColours) {
        this.automaton = ParityUtil.minimizePriorities(AutomatonUtil.asMutable(automaton));
      } else {
        this.automaton = automaton;
      }
    }

    Automaton<T, ParityAcceptance> complete() {
      var automaton = AutomatonUtil.asMutable(this.automaton);
      BitSet reject = new BitSet();
      reject.set(0);
      AutomatonUtil.complete(automaton, sinkState, reject);
      return automaton;
    }

    Automaton<T, ParityAcceptance> complement() {
      if (automaton instanceof MutableAutomaton
        || automaton.getAcceptance().getParity() != ParityAcceptance.Parity.MIN_ODD) {
        return ParityUtil.complement(AutomatonUtil.asMutable(automaton), sinkState);
      }

      assert automaton.getAcceptance().getParity() == ParityAcceptance.Parity.MIN_ODD;
      return AutomatonUtil.cast(Views.complement(automaton, sinkState), ParityAcceptance.class);
    }
  }
}
