/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.jni;

import java.util.BitSet;
import java.util.Set;
import java.util.stream.Collectors;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.Converter;

class RealizabilityRewriter {
  private RealizabilityRewriter() {
  }

  static Formula removeSingleValuedInputLiterals(BitSet inputVariables, Formula formula) {
    if (!SyntacticFragment.NNF.contains(formula)) {
      return formula;
    }

    Formula oldFormula;
    Formula newFormula = formula;

    do {
      oldFormula = newFormula;

      Set<Literal> atoms = formula.subformulas(Literal.class);
      Set<Literal> singleAtoms = atoms.stream()
        .filter(x -> inputVariables.get(x.getAtom()) && !atoms.contains(x.not()))
        .collect(Collectors.toSet());
      newFormula = oldFormula.accept(new InputLiteralSimplifier(singleAtoms));
    } while (!oldFormula.equals(newFormula));

    return newFormula;
  }

  private static class InputLiteralSimplifier extends Converter {
    private final Set<Literal> singleValuedInputVariables;

    private InputLiteralSimplifier(Set<Literal> singleValuedInputVariables) {
      super(SyntacticFragment.NNF.classes());
      this.singleValuedInputVariables = singleValuedInputVariables;
    }

    @Override
    public Formula visit(Literal literal) {
      if (singleValuedInputVariables.contains(literal)) {
        return BooleanConstant.FALSE;
      }

      return literal;
    }
  }
}