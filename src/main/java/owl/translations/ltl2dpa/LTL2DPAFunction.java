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

package owl.translations.ltl2dpa;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_EXACT;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_HEURISTIC;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.SYMMETRIC;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.AnnotatedStateOptimisation;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.ParityUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.ParityAcceptanceOptimizations;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.translations.mastertheorem.Selector;
import owl.util.DaemonThreadFactory;

public class LTL2DPAFunction implements Function<LabelledFormula, Automaton<?, ParityAcceptance>> {

  public static final EnumSet<Configuration> RECOMMENDED_ASYMMETRIC_CONFIG = EnumSet.of(
    OPTIMISE_INITIAL_STATE, COMPLEMENT_CONSTRUCTION_EXACT, COMPRESS_COLOURS);

  public static final EnumSet<Configuration> RECOMMENDED_SYMMETRIC_CONFIG = EnumSet.of(SYMMETRIC,
    OPTIMISE_INITIAL_STATE, COMPLEMENT_CONSTRUCTION_EXACT, COMPRESS_COLOURS);

  private final EnumSet<Configuration> configuration;
  private final Environment environment;

  private final AsymmetricDPAConstruction asymmetricDPAConstruction;
  private final SymmetricDPAConstruction symmetricDPAConstruction;

  public LTL2DPAFunction(Environment environment, EnumSet<Configuration> configuration) {
    checkArgument(!configuration.contains(COMPLEMENT_CONSTRUCTION_EXACT)
      || !configuration.contains(COMPLEMENT_CONSTRUCTION_HEURISTIC),
      "COMPLEMENT_CONSTRUCTION_EXACT and HEURISTIC cannot be used together.");

    this.configuration = EnumSet.copyOf(configuration);
    this.environment = environment;

    asymmetricDPAConstruction = new AsymmetricDPAConstruction(environment);
    symmetricDPAConstruction = new SymmetricDPAConstruction(environment);
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    var executor = Executors.newCachedThreadPool(
      new DaemonThreadFactory(Thread.currentThread().getThreadGroup()));

    Callable<Result<?>> automatonCallable;
    Callable<Result<?>> complementCallable;

    if (configuration.contains(COMPLEMENT_CONSTRUCTION_HEURISTIC)) {
      int fixpoints = configuration.contains(SYMMETRIC)
        ? Selector.selectSymmetric(formula.formula(), false).size()
        : Selector.selectAsymmetric(formula.formula(), false).size();

      int negationFixpoints = configuration.contains(SYMMETRIC)
        ? Selector.selectSymmetric(formula.formula().not(), false).size()
        : Selector.selectAsymmetric(formula.formula().not(), false).size();

      if (fixpoints <= negationFixpoints) {
        automatonCallable = configuration.contains(SYMMETRIC)
          ? () -> symmetricConstruction(formula)
          : () -> asymmetricConstruction(formula);
        complementCallable = () -> null;
      } else {
        automatonCallable = () -> null;
        complementCallable = configuration.contains(SYMMETRIC)
          ? () -> symmetricConstruction(formula.not())
          : () -> asymmetricConstruction(formula.not());
      }
    } else {
      automatonCallable = configuration.contains(SYMMETRIC)
        ? () -> symmetricConstruction(formula)
        : () -> asymmetricConstruction(formula);

      if (configuration.contains(COMPLEMENT_CONSTRUCTION_EXACT)) {
        complementCallable = configuration.contains(SYMMETRIC)
          ? () -> symmetricConstruction(formula.not())
          : () -> asymmetricConstruction(formula.not());
      } else {
        complementCallable = () -> null;
      }
    }

    Future<Result<?>> automatonFuture = executor.submit(automatonCallable);
    Future<Result<?>> complementFuture = executor.submit(complementCallable);

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

      return automaton.acceptance().acceptanceSets()
        <= complement.acceptance().acceptanceSets() ? automaton : complement;
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
    if (configuration.contains(COMPLEMENT_CONSTRUCTION_HEURISTIC)) {
      return automaton != null || complement != null;
    }

    if (configuration.contains(COMPLEMENT_CONSTRUCTION_EXACT)) {
      return automaton != null && complement != null;
    }

    return automaton != null;
  }

  @Nullable
  private static Automaton<?, ParityAcceptance> getAutomaton(Future<Result<?>> future)
    throws ExecutionException {
    var result = Uninterruptibles.getUninterruptibly(future);
    return result == null ? null : result.automaton;
  }

  @Nullable
  private static Automaton<?, ParityAcceptance> getComplement(Future<Result<?>> future)
    throws ExecutionException {
    var result = Uninterruptibles.getUninterruptibly(future);
    return result == null ? null : result.complement();
  }

  private Result<AsymmetricRankingState> asymmetricConstruction(LabelledFormula formula) {
    var dpa = asymmetricDPAConstruction.of(formula);
    var optimisedDpa = configuration.contains(OPTIMISE_INITIAL_STATE)
      ? MutableAutomatonUtil.asMutable(AnnotatedStateOptimisation.optimizeInitialState(dpa))
      : dpa;

    if (optimisedDpa.initialStates().isEmpty()) {
      var factory = environment.factorySupplier()
        .getEquivalenceClassFactory(formula.atomicPropositions());
      return new Result<>(optimisedDpa,
        AsymmetricRankingState.of(factory.of(BooleanConstant.FALSE)),
        configuration.contains(COMPRESS_COLOURS));
    }

    return new Result<>(optimisedDpa,
      AsymmetricRankingState.of(dpa.onlyInitialState().state().factory().of(BooleanConstant.FALSE)),
      configuration.contains(COMPRESS_COLOURS));
  }

  private Result<SymmetricRankingState> symmetricConstruction(LabelledFormula formula) {
    var dpa = symmetricDPAConstruction.of(formula);
    var optimisedDpa = configuration.contains(OPTIMISE_INITIAL_STATE)
      ? MutableAutomatonUtil.asMutable(AnnotatedStateOptimisation.optimizeInitialState(dpa))
      : dpa;
    return new Result<>(optimisedDpa,
      SymmetricRankingState.of(Map.of()),
      configuration.contains(COMPRESS_COLOURS));
  }

  public enum Configuration {
    // Underlying LDBA construction
    SYMMETRIC,

    // Parallel translation
    COMPLEMENT_CONSTRUCTION_EXACT,
    COMPLEMENT_CONSTRUCTION_HEURISTIC,

    // Postprocessing
    OPTIMISE_INITIAL_STATE,
    COMPRESS_COLOURS
  }

  private static final class Result<T> {
    final Automaton<T, ParityAcceptance> automaton;
    final T sinkState;

    Result(Automaton<T, ParityAcceptance> automaton, T sinkState, boolean compressColours) {
      this.sinkState = sinkState;

      if (compressColours) {
        this.automaton = ParityAcceptanceOptimizations
          .minimizePriorities(MutableAutomatonUtil.asMutable(automaton));
      } else {
        this.automaton = automaton;
      }
    }

    Automaton<T, ParityAcceptance> complement() {
      if (automaton instanceof MutableAutomaton
        || automaton.acceptance().parity() != ParityAcceptance.Parity.MIN_ODD) {
        return ParityUtil.complement(MutableAutomatonUtil.asMutable(automaton), sinkState);
      }

      assert automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_ODD;
      return BooleanOperations
        .deterministicComplement(automaton, sinkState, ParityAcceptance.class);
    }
  }
}
