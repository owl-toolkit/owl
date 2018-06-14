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
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.visitors.Converter;

public class FGSubstitution extends Converter {
  private final Set<GOperator> gOperators;
  private final Set<ROperator> rOperators;
  private final Set<WOperator> wOperators;

  public FGSubstitution(Iterable<? extends Formula> y) {
    super(SyntacticFragment.NNF.classes());

    Set<GOperator> gOperators = new HashSet<>();
    Set<ROperator> rOperators = new HashSet<>();
    Set<WOperator> wOperators = new HashSet<>();

    y.forEach(formula -> {
      if (formula instanceof GOperator) {
        gOperators.add((GOperator) formula);
      } else if (formula instanceof ROperator) {
        rOperators.add((ROperator) formula);
      } else if (formula instanceof WOperator) {
        wOperators.add((WOperator) formula);
      } else {
        throw new IllegalArgumentException();
      }
    });

    this.gOperators = Set.copyOf(gOperators);
    this.rOperators = Set.copyOf(rOperators);
    this.wOperators = Set.copyOf(wOperators);
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return BooleanConstant.of(gOperators.contains(gOperator));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    if (rOperators.contains(rOperator) || gOperators.contains(new GOperator(rOperator.right))) {
      return BooleanConstant.TRUE;
    }

    return MOperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    if (wOperators.contains(wOperator) || gOperators.contains(new GOperator(wOperator.left))) {
      return BooleanConstant.TRUE;
    }

    return UOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
  }
}
