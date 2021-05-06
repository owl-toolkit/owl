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

import static owl.automaton.symbolic.StatisticsCollector.STATISTICS_COLLECTOR;

import java.util.Optional;
import java.util.function.Function;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.symbolic.StatisticsCollector;
import owl.automaton.symbolic.SymbolicAutomaton;
import owl.automaton.symbolic.SymbolicBooleanOperations;
import owl.automaton.symbolic.SymbolicDRA2DPAConstruction;
import owl.bdd.FactorySupplier;
import owl.ltl.LabelledFormula;
import owl.translations.ltl2dra.SymbolicNormalformDRAConstruction;

public final class SymbolicDPAConstruction
  implements Function<LabelledFormula, SymbolicAutomaton<ParityAcceptance>> {

  @Override
  public SymbolicAutomaton<ParityAcceptance> apply(LabelledFormula labelledFormula) {
    var factory = FactorySupplier.defaultSupplier().getBddSetFactory();
    var draConstructor = new SymbolicNormalformDRAConstruction(factory);
    var symbolicRabinProductAutomaton = draConstructor.apply(labelledFormula);
    STATISTICS_COLLECTOR.advanceToDPW1(symbolicRabinProductAutomaton);
    Optional<SymbolicAutomaton<ParityAcceptance>> parity = SymbolicDRA2DPAConstruction
      .of(symbolicRabinProductAutomaton)
      .tryToParity();

    if (parity.isPresent()) {
      return parity.get();
    }
    STATISTICS_COLLECTOR.advanceToDSW();
    var symbolicStreettProductAutomaton = draConstructor.apply(labelledFormula.not());
    STATISTICS_COLLECTOR.advanceToDRWDSWProduct(symbolicStreettProductAutomaton);
    SymbolicAutomaton<?> symbolicDpwStructure = SymbolicBooleanOperations
      .deterministicStructureProduct(
        symbolicRabinProductAutomaton,
        symbolicStreettProductAutomaton
      );
    STATISTICS_COLLECTOR.advanceToDPW2(symbolicDpwStructure);
    return SymbolicDRA2DPAConstruction.of(symbolicDpwStructure).toParity();
  }
}
