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

package owl.ltl.rewriter;

import java.util.Arrays;
import java.util.BitSet;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.Converter;

public final class LiteralMapper {
  private static final int UNDEFINED = -1;

  private LiteralMapper() {}

  public static ShiftedFormula shiftLiterals(Formula formula) {
    BitSet atoms = formula.atomicPropositions(true);

    int[] mapping = new int[atoms.length()];
    Arrays.fill(mapping, UNDEFINED);

    int nextAtom = 0;

    for (int i = atoms.nextSetBit(0); i >= 0; i = atoms.nextSetBit(i + 1)) {
      mapping[i] = nextAtom;
      nextAtom++;
    }

    return new ShiftedFormula(formula.accept(new LiteralShifter(mapping)), mapping);
  }

  private static class LiteralShifter extends Converter {
    private final int[] mapping;

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    private LiteralShifter(int[] mapping) { // NOPMD
      super(SyntacticFragment.ALL);
      this.mapping = mapping;
    }

    @Override
    public Formula visit(Literal literal) {
      assert mapping[literal.getAtom()] != UNDEFINED;
      assert mapping[literal.getAtom()] <= literal.getAtom();
      return Literal.of(mapping[literal.getAtom()], literal.isNegated());
    }
  }

  public static final class ShiftedFormula {
    public final Formula formula;
    public final int[] mapping;

    @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "AssignmentOrReturnOfFieldWithMutableType"})
    ShiftedFormula(Formula formula, int[] mapping) {
      this.formula = formula;
      this.mapping = mapping;
    }
  }
}
