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

package owl.translations.ltl2dra;

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import owl.automaton.symbolic.SymbolicAutomaton;
import owl.automaton.symbolic.SymbolicBooleanOperations;
import owl.bdd.BddSetFactory;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.Factories;
import owl.bdd.FactorySupplier;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.translations.canonical.DeterministicConstructions;

public class SymbolicNormalformDRAConstruction extends AbstractNormalformDRAConstruction
  implements Function<LabelledFormula, SymbolicAutomaton<?>> {

  private final BddSetFactory factory;

  public SymbolicNormalformDRAConstruction(BddSetFactory factory) {
    this(true, factory);
  }

  public SymbolicNormalformDRAConstruction(boolean useDualConstruction, BddSetFactory factory) {
    super(useDualConstruction);
    this.factory = factory;
  }

  @Override
  public SymbolicAutomaton<?> apply(LabelledFormula labelledFormula) {
    var pairs = group(labelledFormula.nnf());

    if (pairs.isEmpty()) {
      pairs = List.of(
        Sigma2Pi2Pair.of(
          labelledFormula.atomicPropositions(),
          BooleanConstant.FALSE,
          BooleanConstant.FALSE)
      );
    }

    Factories explicitFactories = FactorySupplier.defaultSupplier().getFactories(
      labelledFormula.atomicPropositions(), EquivalenceClassFactory.Encoding.AP_SEPARATE);
    Map<LabelledFormula, Integer> coSafetySafetyIndices = new HashMap<>();
    Map<LabelledFormula, Integer> safetyCoSafetyIndices = new HashMap<>();

    List<SymbolicAutomaton<?>> automata = new ArrayList<>();
    List<PropositionalFormula<Integer>> disjuncts = new ArrayList<>();

    for (Sigma2Pi2Pair formulaPair : pairs) {
      var sigma2 = formulaPair.sigma2();
      var pi2 = formulaPair.pi2();

      var coSafetySafetyIndex = coSafetySafetyIndices.get(sigma2);

      if (coSafetySafetyIndex == null) {
        coSafetySafetyIndex = automata.size();
        coSafetySafetyIndices.put(sigma2, coSafetySafetyIndex);
        automata.add(SymbolicAutomaton.of(
          DeterministicConstructions.CoSafetySafetyRoundRobin.of(
            explicitFactories, sigma2.formula(), true, false
          ), factory, labelledFormula.atomicPropositions()));
      }

      var safetyCoSafetyIndex = safetyCoSafetyIndices.get(pi2);

      if (safetyCoSafetyIndex == null) {
        safetyCoSafetyIndex = automata.size();
        safetyCoSafetyIndices.put(pi2, safetyCoSafetyIndex);
        automata.add(SymbolicAutomaton.of(
          DeterministicConstructions.SafetyCoSafetyRoundRobin.of(
            explicitFactories, pi2.formula(), true, false
          ), factory, labelledFormula.atomicPropositions()));
      }

      disjuncts.add(Conjunction.of(
        Variable.of(coSafetySafetyIndex), Variable.of(safetyCoSafetyIndex)));
    }

    return SymbolicBooleanOperations.deterministicProduct(Disjunction.of(disjuncts), automata);
  }
}
