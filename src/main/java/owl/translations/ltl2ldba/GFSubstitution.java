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

package owl.translations.ltl2ldba;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.visitors.Converter;

public class GFSubstitution extends Converter {
  private final Set<FOperator> fOperators;
  private final Set<MOperator> mOperators;
  private final Set<UOperator> uOperators;

  public GFSubstitution(Iterable<? extends Formula> x) {
    super(SyntacticFragment.NNF.classes());

    Set<FOperator> fOperators = new HashSet<>();
    Set<MOperator> mOperators = new HashSet<>();
    Set<UOperator> uOperators = new HashSet<>();

    x.forEach(formula -> {
      if (formula instanceof FOperator) {
        fOperators.add((FOperator) formula);
      } else if (formula instanceof MOperator) {
        mOperators.add((MOperator) formula);
      } else if (formula instanceof UOperator) {
        uOperators.add((UOperator) formula);
      } else {
        throw new IllegalArgumentException();
      }
    });

    this.fOperators = Set.copyOf(fOperators);
    this.mOperators = Set.copyOf(mOperators);
    this.uOperators = Set.copyOf(uOperators);
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return BooleanConstant.of(fOperators.contains(fOperator));
  }

  @Override
  public Formula visit(MOperator mOperator) {
    if (mOperators.contains(mOperator) || fOperators.contains(new FOperator(mOperator.left))) {
      return ROperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    return BooleanConstant.FALSE;
  }

  @Override
  public Formula visit(UOperator uOperator) {
    if (uOperators.contains(uOperator) || fOperators.contains(new FOperator(uOperator.right))) {
      return WOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    return BooleanConstant.FALSE;
  }
}
