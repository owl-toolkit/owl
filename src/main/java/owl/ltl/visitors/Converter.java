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

package owl.ltl.visitors;

import java.util.Set;
import java.util.function.Function;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.OOperator;
import owl.ltl.ROperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;

public abstract class Converter implements Visitor<Formula>, Function<Formula, Formula> {

  private final Set<Class<? extends Formula>> supportedCases;

  protected Converter(Set<Class<? extends Formula>> supportedCases) {
    this.supportedCases = Set.copyOf(supportedCases);
  }

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(Biconditional biconditional) {
    checkSupportedCase(Biconditional.class);
    return Biconditional.of(biconditional.left.accept(this), biconditional.right.accept(this));
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    checkSupportedCase(BooleanConstant.class);
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    checkSupportedCase(Conjunction.class);
    return Conjunction.of(conjunction.map(c -> c.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    checkSupportedCase(Disjunction.class);
    return Disjunction.of(disjunction.map(c -> c.accept(this)));
  }

  @Override
  public Formula visit(FOperator fOperator) {
    checkSupportedCase(FOperator.class);
    return FOperator.of(fOperator.operand.accept(this));
  }

  @Override
  public Formula visit(FrequencyG freq) {
    checkSupportedCase(FrequencyG.class);
    return new FrequencyG(freq.operand.accept(this), freq.bound, freq.cmp, freq.limes);
  }

  @Override
  public Formula visit(GOperator gOperator) {
    checkSupportedCase(GOperator.class);
    return GOperator.of(gOperator.operand.accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    checkSupportedCase(Literal.class);
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    checkSupportedCase(MOperator.class);
    return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    checkSupportedCase(ROperator.class);
    return ROperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    checkSupportedCase(UOperator.class);
    return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    checkSupportedCase(WOperator.class);
    return WOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    checkSupportedCase(XOperator.class);
    return XOperator.of(xOperator.operand.accept(this));
  }

  //Past operators
  @Override
  public Formula visit(OOperator oOperator) {
    checkSupportedCase(OOperator.class);
    return OOperator.of(oOperator.operand.accept(this));
  }

  @Override
  public Formula visit(HOperator hOperator) {
    checkSupportedCase(HOperator.class);
    return HOperator.of(hOperator.operand.accept(this));
  }

  @Override
  public Formula visit(SOperator sOperator) {
    checkSupportedCase(SOperator.class);
    return SOperator.of(sOperator.left.accept(this), sOperator.right.accept(this));
  }

  @Override
  public Formula visit(TOperator tOperator) {
    checkSupportedCase(TOperator.class);
    return TOperator.of(tOperator.left.accept(this), tOperator.right.accept(this));
  }

  @Override
  public Formula visit(YOperator yOperator) {
    checkSupportedCase(YOperator.class);
    return YOperator.of(yOperator.operand.accept(this));
  }

  @Override
  public Formula visit(ZOperator zOperator) {
    checkSupportedCase(ZOperator.class);
    return ZOperator.of(zOperator.operand.accept(this));
  }

  private void checkSupportedCase(Class<? extends Formula> clazz) {
    if (!supportedCases.contains(clazz)) {
      throw new UnsupportedOperationException("Unsupported case: " + clazz);
    }
  }
}
