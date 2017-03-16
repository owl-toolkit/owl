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

package owl.ltl.visitors;

import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;

public class ValuationSetVisitor extends DefaultVisitor<ValuationSet> {

  private final ValuationSetFactory factory;

  public ValuationSetVisitor(ValuationSetFactory factory) {
    this.factory = factory;
  }

  @Override
  protected ValuationSet defaultAction(Formula formula) {
    throw new UnsupportedOperationException(formula.toString());
  }

  @Override
  public ValuationSet visit(BooleanConstant booleanConstant) {
    return booleanConstant.value
           ? factory.createUniverseValuationSet()
           : factory.createEmptyValuationSet();
  }

  @Override
  public ValuationSet visit(Conjunction conjunction) {
    return conjunction.map(x -> x.accept(this))
      .reduce(ValuationSet::intersection).orElse(factory.createUniverseValuationSet());
  }

  @Override
  public ValuationSet visit(Disjunction disjunction) {
    return disjunction.map(x -> x.accept(this))
      .reduce(ValuationSet::union).orElse(factory.createEmptyValuationSet());
  }

  @Override
  public ValuationSet visit(Literal literal) {
    return factory.createValuationSet(literal);
  }
}
