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

package owl.logic.propositional;

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import com.google.common.collect.ImmutableBiMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConjunctiveNormalForm<V> {

  /**
   * Clause-representation of the formula. Variables are from the range [1,n], where n is bounded by
   * -Integer.MIN_VALUE. Positive literals are encoded as [1,n] and negated literals as [-1,-n].
   * 0 is used as a separator between clauses.
   * Example:
   * -1 -2 0 1 2 0 2 3 0 ...
   */
  public final List<int[]> clauses;
  public final ImmutableBiMap<V, Integer> variableMapping;
  public final int tsetinVariablesLowerBound; // inclusive
  public final int tsetinVariablesUpperBound; // exclusive

  /**
   * Construct an equisatisfiable CNF-representation of the given formula.
   * @param formula the formula.
   */
  public ConjunctiveNormalForm(PropositionalFormula<V> formula) {
    var temporaryMapping = new VariableMappingBuilder<>(formula);
    tsetinVariablesLowerBound = temporaryMapping.nextFreeVariable;
    clauses = encodeFormula(formula, temporaryMapping);
    tsetinVariablesUpperBound = temporaryMapping.nextFreeVariable;
    variableMapping = temporaryMapping.variables;
  }

  public boolean evaluate(BitSet assignment) {
    for (int[] clause : clauses) {
      boolean clauseValue = false;

      for (int literal : clause) {
        if (literal > 0) {
          clauseValue = assignment.get(literal - 1);
        } else {
          clauseValue = !assignment.get((-literal) - 1);
        }

        if (clauseValue) {
          break;
        }
      }

      if (!clauseValue) {
        return false;
      }
    }

    return true;
  }

  // Called T in source material.
  // Plaisted, D.A., Greenbaum, S.:
  // A structure-preserving clause form translation. J.Symb. Comput. 2(3), 293–304 (1986)
  private static <V> List<int[]> encodeFormula(
    PropositionalFormula<V> formula, VariableMappingBuilder<V> variableMapping) {

    var nnfFormula = formula.nnf();
    var cnfBuilder = new ArrayList<int[]>();

    if (nnfFormula instanceof Variable) {
      cnfBuilder.add(new int[]{ variableMapping.lookup(nnfFormula) });
    } else if (nnfFormula instanceof Negation<V> negation) {
      var variable = (Variable<V>) negation.operand();
      cnfBuilder.add(new int[]{ -variableMapping.lookup(variable) });
    } else if (nnfFormula instanceof Conjunction || nnfFormula instanceof Disjunction) {
      cnfBuilder.add(new int[]{ variableMapping.lookup(nnfFormula) });
      encodeFormula(nnfFormula, variableMapping, cnfBuilder);
    } else {
      assert false : "Unreachable.";
    }

    return List.copyOf(cnfBuilder);
  }

  // Called T_1 in source material.
  private static <V> void encodeFormula(
    PropositionalFormula<V> formula, VariableMappingBuilder<V> variableMapping, List<int[]> cnf) {

    if (formula instanceof Variable || formula instanceof Negation) {
      return;
    }

    // T_1_def
    if (formula instanceof Conjunction<V> conjunction) {
      int conjunctionVariable = variableMapping.lookup(conjunction);

      for (var conjunct : conjunction.conjuncts()) {
        cnf.add(new int[] { -conjunctionVariable, variableMapping.lookup(conjunct) });
      }

      for (var conjunct : conjunction.conjuncts()) {
        encodeFormula(conjunct, variableMapping, cnf);
      }
    } else {
      var disjunction = (Disjunction<V>) formula;
      int disjunctionVariable = variableMapping.lookup(disjunction);

      int s = disjunction.disjuncts().size();
      int[] clause = new int[s + 1];
      clause[0] = -disjunctionVariable;

      for (int i = 0; i < s; i++) {
        clause[i + 1] = variableMapping.lookup(disjunction.disjuncts().get(i));
      }

      cnf.add(clause);

      for (var disjunct : disjunction.disjuncts()) {
        encodeFormula(disjunct, variableMapping, cnf);
      }
    }
  }

  private static class VariableMappingBuilder<V> {

    private final ImmutableBiMap<V, Integer> variables;
    private final Map<Conjunction<V>, Integer> tseitinConjunctionVariables = new HashMap<>();
    private final Map<Disjunction<V>, Integer> tseitinDisjunctionVariables = new HashMap<>();

    private int nextFreeVariable;

    private VariableMappingBuilder(PropositionalFormula<V> formula) {
      var variables = new HashMap<V, Integer>();

      // Assign [v1, v2, ..., vn] -> [1, 2, ... n]
      formula.variables().forEach(variable -> {
        variables.put(variable, variables.size() + 1);
      });

      this.variables = ImmutableBiMap.copyOf(variables);
      nextFreeVariable = this.variables.size() + 1;
    }

    int lookup(PropositionalFormula<V> nnfFormula) {

      if (nnfFormula instanceof Conjunction<V> conjunction) {
        Integer oldVariable = tseitinConjunctionVariables
          .putIfAbsent(conjunction, nextFreeVariable);

        if (oldVariable == null) {
          int variable = nextFreeVariable;
          nextFreeVariable++;
          return variable;
        } else {
          return oldVariable;
        }
      }

      if (nnfFormula instanceof Disjunction<V> disjunction) {
        Integer oldVariable = tseitinDisjunctionVariables
          .putIfAbsent(disjunction, nextFreeVariable);

        if (oldVariable == null) {
          int variable = nextFreeVariable;
          nextFreeVariable++;
          return variable;
        } else {
          return oldVariable;
        }
      }

      Variable<V> variable = nnfFormula instanceof Negation<V> negation
        ? (Variable<V>) negation.operand()
        : (Variable<V>) nnfFormula;

      int variableId = variables.get(variable.variable());
      return nnfFormula instanceof Negation ? -variableId : variableId;
    }
  }

  @Override
  public String toString() {
    return "ConjunctiveNormalForm{clauses=" + clauses
      + ", variableMapping=" + variableMapping + '}';
  }
}
