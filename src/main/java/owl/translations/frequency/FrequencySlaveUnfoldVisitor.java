/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.frequency;

import java.util.Set;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Converter;

class FrequencySlaveUnfoldVisitor extends Converter {
  protected FrequencySlaveUnfoldVisitor() {
    super(Set.of(GOperator.class, XOperator.class, FOperator.class));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return gOperator;
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return xOperator;
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return fOperator;
  }

  @Override
  public Formula visit(FrequencyG frequencyOperator) {
    return frequencyOperator;
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return new Disjunction(uOperator.right.accept(this),
      new Conjunction(uOperator.left.accept(this), uOperator));
  }
}
