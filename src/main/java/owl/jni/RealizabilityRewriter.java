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

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.Collector;
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
      BitSet atoms = Collector.collectAtoms(oldFormula, false);
      BitSet negatedAtoms = Collector.collectAtoms(oldFormula, true);

      BitSet singleAtoms = BitSets.copyOf(atoms);
      singleAtoms.xor(negatedAtoms);

      BitSet inputSingleAtoms = BitSets.copyOf(singleAtoms);
      inputSingleAtoms.and(inputVariables);
      newFormula = oldFormula.accept(new InputLiteralSimplifier(inputSingleAtoms));
    } while (!oldFormula.equals(newFormula));

    return newFormula;
  }

  private static class InputLiteralSimplifier extends Converter {
    private final BitSet singleValuedInputVariables;

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    private InputLiteralSimplifier(BitSet singleValuedInputVariables) {
      super(SyntacticFragment.NNF.classes());
      this.singleValuedInputVariables = singleValuedInputVariables;
    }

    @Override
    public Formula visit(Literal literal) {
      if (singleValuedInputVariables.get(literal.getAtom())) {
        return BooleanConstant.FALSE;
      }

      return literal;
    }
  }
}