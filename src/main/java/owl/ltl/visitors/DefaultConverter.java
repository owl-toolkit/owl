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

import java.util.function.Function;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;

public abstract class DefaultConverter implements Visitor<Formula>, Function<Formula, Formula> {
  public static Transformer asTransformer(Visitor<Formula> converter) {
    return Transformers.fromFunction(LabelledFormula.class,
      labelledFormula -> labelledFormula.wrap(labelledFormula.formula.accept(converter)));
  }

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    return Conjunction.of(conjunction.children.stream().map(c -> c.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    return Disjunction.of(disjunction.children.stream().map(c -> c.accept(this)));
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return FOperator.of(fOperator.operand.accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return GOperator.of(gOperator.operand.accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
  }

  @Override
  public Formula visit(FrequencyG freq) {
    return new FrequencyG(freq.operand.accept(this), freq.bound, freq.cmp, freq.limes);
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return WOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return ROperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return XOperator.of(xOperator.operand.accept(this));
  }
}
