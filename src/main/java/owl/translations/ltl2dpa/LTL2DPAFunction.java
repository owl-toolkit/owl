/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_EXACT;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.POST_PROCESS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.SYMMETRIC;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import owl.automaton.AnnotatedState;
import owl.automaton.AnnotatedStateOptimisation;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.ParityAcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.util.ParallelEvaluation;

public class LTL2DPAFunction implements Function<LabelledFormula, Automaton<?, ParityAcceptance>> {

  private final EnumSet<Configuration> configuration;

  private final AsymmetricDPAConstruction asymmetricDPAConstruction;
  private final SymmetricDPAConstruction symmetricDPAConstruction;

  public LTL2DPAFunction(Set<Configuration> configuration) {
    this.configuration = EnumSet.copyOf(configuration);

    asymmetricDPAConstruction = new AsymmetricDPAConstruction();
    symmetricDPAConstruction = new SymmetricDPAConstruction();
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {
    Supplier<Optional<Automaton<?, ? extends ParityAcceptance>>> automatonSupplier;
    Supplier<Optional<Automaton<?, ? extends ParityAcceptance>>> complementSupplier;

    automatonSupplier = configuration.contains(SYMMETRIC)
      ? () -> Optional.of(postProcess(symmetricDPAConstruction.of(formula, false)))
      : () -> Optional.of(postProcess(asymmetricDPAConstruction.of(formula, false)));

    if (configuration.contains(COMPLEMENT_CONSTRUCTION_EXACT)) {
      complementSupplier = configuration.contains(SYMMETRIC)
        ? () -> Optional.of(
          BooleanOperations.deterministicComplementOfCompleteAutomaton(
            postProcess(symmetricDPAConstruction.of(formula.not(), true)),
            ParityAcceptance.class))
        : () -> Optional.of(
          BooleanOperations.deterministicComplementOfCompleteAutomaton(
            postProcess(asymmetricDPAConstruction.of(formula.not(), true)),
            ParityAcceptance.class));
    } else {
      complementSupplier = Optional::empty;
    }

    return OmegaAcceptanceCast.cast(
      ParallelEvaluation.takeSmallest((List)
        ParallelEvaluation.evaluate(List.of(automatonSupplier, complementSupplier))),
      ParityAcceptance.class);
  }

  private <T extends AnnotatedState<?>> Automaton<T, ParityAcceptance> postProcess(
    Automaton<T, ParityAcceptance> automaton) {

    if (!configuration.contains(POST_PROCESS)) {
      return automaton;
    }

    return ParityAcceptanceOptimizations.minimizePriorities(
      AnnotatedStateOptimisation.optimizeInitialState(automaton));
  }

  public enum Configuration {
    // Underlying LDBA construction
    SYMMETRIC,
    // Parallel translation
    COMPLEMENT_CONSTRUCTION_EXACT,
    // Postprocessing (optimise initial state and compress colours)
    POST_PROCESS
  }
}
