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

package owl.ltl.visitors;

import java.util.Set;
import java.util.function.UnaryOperator;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public abstract class Converter implements Visitor<Formula>, UnaryOperator<Formula> {

  private final Set<Class<? extends Formula>> supportedCases;

  protected Converter(SyntacticFragment fragment) {
    this(fragment.classes());
  }

  protected Converter(Set<Class<? extends Formula>> supportedCases) {
    this.supportedCases = Set.copyOf(supportedCases);
  }

  @Override
  public Formula visit(Biconditional biconditional) {
    checkSupportedCase(Biconditional.class);
    return Biconditional.of(
      biconditional.leftOperand().accept(this),
      biconditional.rightOperand().accept(this));
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
    return FOperator.of(fOperator.operand().accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    checkSupportedCase(GOperator.class);
    return GOperator.of(gOperator.operand().accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    checkSupportedCase(Literal.class);
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    checkSupportedCase(MOperator.class);
    return MOperator
      .of(mOperator.leftOperand().accept(this), mOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    checkSupportedCase(ROperator.class);
    return ROperator
      .of(rOperator.leftOperand().accept(this), rOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    checkSupportedCase(UOperator.class);
    return UOperator
      .of(uOperator.leftOperand().accept(this), uOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    checkSupportedCase(WOperator.class);
    return WOperator
      .of(wOperator.leftOperand().accept(this), wOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    checkSupportedCase(XOperator.class);
    return XOperator.of(xOperator.operand().accept(this));
  }

  @Override
  public Formula visit(Negation negation) {
    checkSupportedCase(Negation.class);
    return new Negation(negation.operand().accept(this));
  }

  private void checkSupportedCase(Class<? extends Formula> clazz) {
    if (!supportedCases.contains(clazz)) {
      throw new UnsupportedOperationException("Unsupported case: " + clazz);
    }
  }
}
