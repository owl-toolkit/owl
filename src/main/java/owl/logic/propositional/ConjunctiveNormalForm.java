/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import static com.google.common.primitives.ImmutableIntArray.Builder;
import static com.google.common.primitives.ImmutableIntArray.builder;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.primitives.ImmutableIntArray;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

public class ConjunctiveNormalForm<V> {

  /**
   * Clause-representation of the formula. Variables are from the range [1,n], where n is bounded by
   * -Integer.MIN_VALUE. Positive literals are encoded as [1,n] and negated literals as [-1,-n].
   * 0 is used as a separator between clauses.
   * Example:
   * -1 -2 0 1 2 0 2 3 0 ...
   */
  public final ImmutableIntArray clauses;
  @Nullable
  public final ImmutableBiMap<V, Integer> variableMapping;

  public ConjunctiveNormalForm(int[] clauses) {
    this.clauses = ImmutableIntArray.copyOf(clauses);
    this.variableMapping = null;
  }

  public ConjunctiveNormalForm(PropositionalFormula<V> formula) {
    var temporaryMapping = new VariableMapping<>(formula);
    clauses = encodeFormula(formula, temporaryMapping);
    variableMapping = temporaryMapping.variables;
  }

  public boolean evaluate(BitSet assignment) {
    boolean clauseValue = false;

    for (int i = 0, s = clauses.length(); i < s; i++) {
      int literal = clauses.get(i);

      if (literal == 0) {
        if (!clauseValue) {
          return false;
        }

        clauseValue = false;
      } else if (!clauseValue) {
        if (literal > 0) {
          clauseValue = assignment.get(literal - 1);
        } else {
          clauseValue = !assignment.get((-literal) - 1);
        }
      }
    }

    return clauseValue;
  }

  // Called T in source material.
  // Plaisted, D.A., Greenbaum, S.:
  // A structure-preserving clause form translation. J.Symb. Comput. 2(3), 293–304 (1986)
  private static <V> ImmutableIntArray encodeFormula(
    PropositionalFormula<V> formula, VariableMapping<V> variableMapping) {

    var nnfFormula = formula.nnf();
    var cnfBuilder = builder(4 * variableMapping.variables.size());

    if (nnfFormula instanceof Variable) {
      cnfBuilder.add(variableMapping.lookup(nnfFormula));
    } else if (nnfFormula instanceof Negation) {
      var negation = (Negation<V>) nnfFormula;
      var variable = (Variable<V>) negation.operand;
      cnfBuilder.add(-variableMapping.lookup(variable));
    } else if (nnfFormula instanceof Conjunction || nnfFormula instanceof Disjunction) {
      cnfBuilder.add(variableMapping.lookup(nnfFormula));
      encodeFormula(nnfFormula, variableMapping, cnfBuilder);
    } else {
      assert false : "Unreachable.";
    }

    return cnfBuilder.build();
  }

  // Called T_1 in source material.
  private static <V> void encodeFormula(
    PropositionalFormula<V> formula, VariableMapping<V> variableMapping, Builder cnf) {

    if (formula instanceof Variable || formula instanceof Negation) {
      return;
    }

    // T_1_def
    if (formula instanceof Conjunction) {
      var conjunction = (Conjunction<V>) formula;
      int conjunctionVariable = variableMapping.lookup(conjunction);

      for (var conjunct : conjunction.conjuncts) {
        cnf.add(0);
        cnf.add(-conjunctionVariable);
        cnf.add(variableMapping.lookup(conjunct));
      }

      for (var conjunct : conjunction.conjuncts) {
        encodeFormula(conjunct, variableMapping, cnf);
      }
    } else {
      var disjunction = (Disjunction<V>) formula;
      int disjunctionVariable = variableMapping.lookup(disjunction);

      cnf.add(0);
      cnf.add(-disjunctionVariable);

      for (var disjunct : disjunction.disjuncts) {
        cnf.add(variableMapping.lookup(disjunct));
      }

      for (var disjunct : disjunction.disjuncts) {
        encodeFormula(disjunct, variableMapping, cnf);
      }
    }
  }

  private static class VariableMapping<V> {

    private final ImmutableBiMap<V, Integer> variables;
    private final Map<Conjunction<V>, Integer> tseitinConjunctionVariables = new HashMap<>();
    private final Map<Disjunction<V>, Integer> tseitinDisjunctionVariables = new HashMap<>();

    private int nextFreeVariable;

    private VariableMapping(PropositionalFormula<V> formula) {
      var variables = new HashMap<V, Integer>();

      // Assign [v1, v2, ..., vn] -> [1, 2, ... n]
      formula.visit(subformula -> {
        if (subformula instanceof Variable) {
          variables.putIfAbsent(((Variable<V>) subformula).variable, variables.size() + 1);
        }
      });

      this.variables = ImmutableBiMap.copyOf(variables);
      nextFreeVariable = this.variables.size() + 1;
    }

    int lookup(PropositionalFormula<V> formula) {

      if (formula instanceof Conjunction) {
        var conjunction = (Conjunction<V>) formula;
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

      if (formula instanceof Disjunction) {
        var disjunction = (Disjunction<V>) formula;
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

      Variable<V> variable;

      if (formula instanceof Variable) {
        variable = (Variable<V>) formula;
      } else {
        assert formula instanceof Negation;
        var negation = (Negation<V>) formula;
        variable = (Variable<V>) negation.operand;
      }

      int variableId = variables.get(variable.variable);
      return formula instanceof Negation ? -variableId : variableId;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConjunctiveNormalForm)) {
      return false;
    }
    ConjunctiveNormalForm<?> that = (ConjunctiveNormalForm<?>) o;
    return clauses.equals(that.clauses) && variableMapping.equals(that.variableMapping);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clauses, variableMapping);
  }

  @Override
  public String toString() {
    return "ConjunctiveNormalForm{clauses=" + clauses
      + ", variableMapping=" + variableMapping + '}';
  }
}
