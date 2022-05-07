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

package owl.translations.ltl2dela;

import com.google.common.base.Preconditions;
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import owl.collections.Numbering;
import owl.logic.propositional.PropositionalFormula;

public final class PropositionalFormulaHelper {

  private PropositionalFormulaHelper() {}

  @Nullable
  public static <K> Map<K, Boolean> findPartialAssignment(
    PropositionalFormula<K> formula, PropositionalFormula<K> refinedFormula) {

    // Build variable map.
    Set<K> formulaVariables = new HashSet<>(formula.variables());
    Set<K> refinedFormulaVariables = new HashSet<>(refinedFormula.variables());

    Preconditions.checkArgument(formulaVariables.containsAll(refinedFormulaVariables));

    Numbering<K> variableMap = new Numbering<>();
    Map<K, Boolean> partialAssignment = new HashMap<>();

    for (K variable : formulaVariables) {
      if (!refinedFormulaVariables.contains(variable)) {
        partialAssignment.put(variable, null);
        variableMap.lookup(variable);
      }
    }

    int stoppingVariable = partialAssignment.size();

    for (K variable : refinedFormulaVariables) {
      variableMap.lookup(variable);
    }

    Bdd bdd = BddFactory.buildBddRecursive(1000, ImmutableBddConfiguration.builder().build());
    bdd.createVariables(variableMap.asMap().size() + 1);

    int formulaNode = translateBdd(formula, bdd, variableMap);
    int refinedFormulaNode = translateBdd(refinedFormula, bdd, variableMap);

    return findPartialAssignment(
      formulaNode, refinedFormulaNode, bdd, variableMap, stoppingVariable).orElse(null);
  }

  private static <K> Optional<Map<K, Boolean>> findPartialAssignment(
    int formulaNode, int refinedFormulaNode, Bdd bdd, Numbering<K> variableMapping,
    int stoppingValue) {

    int variable = bdd.isNodeRoot(formulaNode) ? Integer.MAX_VALUE : bdd.variable(formulaNode);

    if (variable < stoppingValue) {
      var low = findPartialAssignment(
        bdd.low(formulaNode), refinedFormulaNode, bdd, variableMapping, stoppingValue);

      if (low.isPresent()) {
        low.get().put(variableMapping.lookup(variable), Boolean.FALSE);
        return low;
      }

      var high = findPartialAssignment(
        bdd.high(formulaNode), refinedFormulaNode, bdd, variableMapping, stoppingValue);

      if (high.isPresent()) {
        high.get().put(variableMapping.lookup(variable), Boolean.TRUE);
      }

      return high;
    }

    return formulaNode == refinedFormulaNode ? Optional.of(new HashMap<>()) : Optional.empty();
  }

  private static <K> int translateBdd(
    PropositionalFormula<K> formula, Bdd bdd, Numbering<K> variableMapping) {

    if (formula instanceof PropositionalFormula.Variable<K> variable) {
      return bdd.variableNode(variableMapping.lookup(variable.variable()));
    }

    if (formula instanceof PropositionalFormula.Negation<K> negation) {
      return bdd.reference(bdd.not(translateBdd(negation.operand(), bdd, variableMapping)));
    }

    if (formula instanceof PropositionalFormula.Biconditional<K> biconditional) {
      return bdd.reference(bdd.equivalence(
        translateBdd(biconditional.leftOperand(), bdd, variableMapping),
        translateBdd(biconditional.rightOperand(), bdd, variableMapping)
      ));
    }

    if (formula instanceof PropositionalFormula.Conjunction<K> conjunction) {
      int x = bdd.trueNode();

      for (var conjunct : conjunction.conjuncts()) {
        int y = translateBdd(conjunct, bdd, variableMapping);
        x = bdd.consume(bdd.and(x, y), x, y);
      }

      return x;
    }

    assert formula instanceof PropositionalFormula.Disjunction;
    int x = bdd.falseNode();

    for (var disjunct : ((PropositionalFormula.Disjunction<K>) formula).disjuncts()) {
      int y = translateBdd(disjunct, bdd, variableMapping);
      x = bdd.consume(bdd.or(x, y), x, y);
    }

    return x;
  }
}
