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

package owl.ltl.rewriter;

import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.Converter;

public final class LiteralMapper {
  public static final int UNDEFINED = -1;

  private LiteralMapper() {}

  // TODO: move this to LabelledFormula.
  public static ShiftedLabelledFormula shiftLiterals(LabelledFormula labelledFormula) {
    var formula = labelledFormula.formula();
    var atomicPropositions = labelledFormula.atomicPropositions();

    var usedAtomicPropositions = formula.atomicPropositions(true);
    int size = usedAtomicPropositions.length();

    var mappingBuilder = ImmutableIntArray.builder(size);
    var mappedAtomicPropositions = new ArrayList<String>(size);

    for (int i = 0; i < size; i++) {
      if (usedAtomicPropositions.get(i)) {
        mappingBuilder.add(mappedAtomicPropositions.size());
        mappedAtomicPropositions.add(atomicPropositions.get(i));
      } else {
        mappingBuilder.add(UNDEFINED);
      }
    }

    var mapping = mappingBuilder.build();

    class LiteralShifter extends Converter {
      private LiteralShifter() {
        super(SyntacticFragment.ALL);
      }

      @Override
      public Formula visit(Literal literal) {
        int mappedAtom = mapping.get(literal.getAtom());
        assert mappedAtom != UNDEFINED;
        assert mappedAtom <= literal.getAtom();
        return Literal.of(mappedAtom, literal.isNegated());
      }
    }

    var shiftedLabelledFormula
      = LabelledFormula.of(formula.accept(new LiteralShifter()), mappedAtomicPropositions);

    return new ShiftedLabelledFormula(shiftedLabelledFormula, mapping);
  }

  public static final class ShiftedLabelledFormula {
    public final LabelledFormula formula;
    public final ImmutableIntArray mapping;

    private ShiftedLabelledFormula(LabelledFormula formula, ImmutableIntArray mapping) {
      this.formula = formula;
      this.mapping = mapping;
    }
  }
}
