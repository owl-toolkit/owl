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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
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

    Supplier<Optional<Result<?>>> automatonSupplier;
    Supplier<Optional<Result<?>>> complementSupplier;

    if (configuration.contains(COMPLEMENT_CONSTRUCTION_HEURISTIC)) {
      int fixpoints = configuration.contains(SYMMETRIC)
        ? getOrMaxInt(() -> Selector.selectSymmetric(formula.formula(), false).size())
        : getOrMaxInt(() -> Selector.selectAsymmetric(formula.formula(), false).size());

      int negationFixpoints = configuration.contains(SYMMETRIC)
        ? getOrMaxInt(() -> Selector.selectSymmetric(formula.formula().not(), false).size())
        : getOrMaxInt(() -> Selector.selectAsymmetric(formula.formula().not(), false).size());

      if (fixpoints == Integer.MAX_VALUE && negationFixpoints == Integer.MAX_VALUE) {
        throw new IllegalStateException("Too many fixpoints.");
      }

      if (fixpoints <= negationFixpoints) {
        automatonSupplier = configuration.contains(SYMMETRIC)
          ? () -> Optional.of(symmetricConstruction(formula))
          : () -> Optional.of(asymmetricConstruction(formula));
        complementSupplier = Optional::empty;
      } else {
        automatonSupplier = Optional::empty;
        complementSupplier = configuration.contains(SYMMETRIC)
          ? () -> Optional.of(symmetricConstruction(formula.not()))
          : () -> Optional.of(asymmetricConstruction(formula.not()));
      }
    } else {
      automatonSupplier = configuration.contains(SYMMETRIC)
        ? () -> Optional.of(symmetricConstruction(formula))
        : () -> Optional.of(asymmetricConstruction(formula));

      if (configuration.contains(COMPLEMENT_CONSTRUCTION_EXACT)) {
        complementSupplier = configuration.contains(SYMMETRIC)
          ? () -> Optional.of(symmetricConstruction(formula.not()))
          : () -> Optional.of(asymmetricConstruction(formula.not()));
      } else {
        complementSupplier = Optional::empty;
      }
    }

    // Setup Thread for complement construction.
    var complementReference = new AtomicReference<Automaton<?, ParityAcceptance>>();
    var complementThread = new Thread(
    () -> complementSupplier.get().map(Result::complement).ifPresent(complementReference::set));
    complementThread.setDaemon(true);
    complementThread.start();

    var automaton = automatonSupplier.get().map(x -> x.automaton).orElse(null);
    Uninterruptibles.joinUninterruptibly(complementThread);
    var complement = complementReference.get();

    if (complement == null) {
      return Objects.requireNonNull(automaton);
    }

    if (automaton == null) {
      return Objects.requireNonNull(complement);
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

  private static int getOrMaxInt(IntSupplier supplier) {
    try {
      return supplier.getAsInt();
    } catch (IllegalArgumentException ex) {
      return Integer.MAX_VALUE;
    }
  }
}
