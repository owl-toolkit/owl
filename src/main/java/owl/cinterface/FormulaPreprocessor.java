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

package owl.cinterface;

import static owl.cinterface.FormulaPreprocessor.VariableStatus.CONSTANT_FALSE;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.CONSTANT_TRUE;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.UNUSED;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.USED;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.visitors.Converter;

final class FormulaPreprocessor {
  private FormulaPreprocessor() {
  }

  static PreprocessedFormula apply(Formula formula, int firstOutputVariable, boolean simplify) {
    Formula restrictedNnf = formula.substitute(Formula::nnf);
    Formula processedFormula = restrictedNnf;

    int variables = restrictedNnf.atomicPropositions(true).length();
    List<VariableStatus> variableStatuses = new ArrayList<>(
      Collections.nCopies(variables, UNUSED));

    if (simplify) {
      processedFormula = SimplifierFactory.apply(
        processedFormula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT);

      Formula oldFormula;
      Formula newFormula = processedFormula;

      do {
        oldFormula = newFormula;
        var polaritySimplifier = new PolaritySimplifier(
          oldFormula, variableStatuses, firstOutputVariable);
        newFormula = oldFormula.accept(polaritySimplifier);
      } while (!oldFormula.equals(newFormula));

      processedFormula = SimplifierFactory.apply(
        newFormula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT);
    }

    processedFormula.atomicPropositions(true).stream().forEach(
      variable -> {
        if (variableStatuses.get(variable) == UNUSED) {
          variableStatuses.set(variable, USED);
        }
      }
    );

    return new PreprocessedFormula(processedFormula, variableStatuses);
  }

  public enum VariableStatus {
    CONSTANT_TRUE, CONSTANT_FALSE, USED, UNUSED
  }

  static class PreprocessedFormula {
    final Formula formula;
    final List<VariableStatus> variableStatuses;

    PreprocessedFormula(Formula formula, List<VariableStatus> variableStatuses) {
      this.formula = formula;
      this.variableStatuses = List.copyOf(variableStatuses);
    }
  }

  private static class PolaritySimplifier extends Converter {
    private final List<VariableStatus> variableStatuses;
    private final Set<Literal> singlePolarityInputVariables;
    private final Set<Literal> singlePolarityOutputVariables;

    private PolaritySimplifier(Formula formula,
      List<VariableStatus> variableStatuses,
      int firstOutputVariable) {
      super(SyntacticFragment.ALL);
      this.variableStatuses = variableStatuses;

      BitSet inputVariables = new BitSet();
      inputVariables.set(0, firstOutputVariable);

      Set<Literal> atoms = formula.nnf().subformulas(Literal.class);

      singlePolarityInputVariables = atoms.stream()
        .filter(x -> inputVariables.get(x.getAtom()) && !atoms.contains(x.not()))
        .collect(Collectors.toSet());

      singlePolarityOutputVariables = atoms.stream()
        .filter(x -> !inputVariables.get(x.getAtom()) && !atoms.contains(x.not()))
        .collect(Collectors.toSet());
    }

    @Override
    public Formula visit(Literal literal) {
      if (singlePolarityInputVariables.contains(literal)) {
        var constant = literal.isNegated() ? CONSTANT_TRUE : CONSTANT_FALSE;

        assert variableStatuses.get(literal.getAtom()) == UNUSED
          || variableStatuses.get(literal.getAtom()) == constant;

        variableStatuses.set(literal.getAtom(), constant);
        return BooleanConstant.FALSE;
      }

      if (singlePolarityOutputVariables.contains(literal)) {
        var constant = literal.isNegated() ? CONSTANT_FALSE : CONSTANT_TRUE;

        assert variableStatuses.get(literal.getAtom()) == UNUSED
          || variableStatuses.get(literal.getAtom()) == constant;

        variableStatuses.set(literal.getAtom(), constant);
        return BooleanConstant.TRUE;
      }

      assert variableStatuses.get(literal.getAtom()) == UNUSED;
      return literal;
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      assert Collections.disjoint(
        biconditional.subformulas(Literal.class), singlePolarityInputVariables);
      assert Collections.disjoint(
        biconditional.subformulas(Literal.class), singlePolarityOutputVariables);
      return biconditional;
    }
  }
}